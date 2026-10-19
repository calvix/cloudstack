# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
import logging
import os.path
from os import listdir
import re
from cs.CsDatabag import CsDataBag
from .CsProcess import CsProcess
from .CsFile import CsFile
from . import CsHelper

HAPROXY_CONF_T = "/etc/haproxy/haproxy.cfg.new"
HAPROXY_CONF_P = "/etc/haproxy/haproxy.cfg"

SSL_CERTS_DIR = "/etc/cloudstack/ssl/"

# matches "-A -u 1.2.3.4:53 -s rr -p 300" and "-a -u 1.2.3.4:53 -r 10.1.1.5:53 -m -w 1" from ipvsadm-save -n
IPVS_SERVICE_RE = re.compile(r"^-A -u (\S+) -s (\S+)(?: -p (\d+))?")
IPVS_DEST_RE = re.compile(r"^-a -u (\S+) -r (\S+)")

class CsLoadBalancer(CsDataBag):
    """ Manage Load Balancer entries """

    def process(self):
        if "config" not in list(self.dbag.keys()):
            return
        if 'configuration' not in list(self.dbag['config'][0].keys()):
            return
        if 'ssl_certs' in list(self.dbag['config'][0].keys()):
            self._create_pem_for_sslcert(self.dbag['config'][0]['ssl_certs'])

        config = self.dbag['config'][0]['configuration']
        file1 = CsFile(HAPROXY_CONF_T)
        file1.empty()
        for x in config:
            [file1.append(w, -1) for w in x.split('\n')]

        file1.commit()
        file2 = CsFile(HAPROXY_CONF_P)
        if not file2.compare(file1):
            # Verify new haproxy config before haproxy restart/reload
            haproxy_err = self._verify_haproxy_config(HAPROXY_CONF_T)
            if haproxy_err:
                raise Exception("haproxy config is invalid with error \n%s" % haproxy_err)

            CsHelper.copy(HAPROXY_CONF_T, HAPROXY_CONF_P)

            proc = CsProcess(['/run/haproxy.pid'])
            if not proc.find():
                logging.debug("CsLoadBalancer:: will restart HAproxy!")
                CsHelper.service("haproxy", "restart")
            else:
                logging.debug("CsLoadBalancer:: will reload HAproxy!")
                CsHelper.service("haproxy", "reload")

        add_rules = self.dbag['config'][0]['add_rules']
        remove_rules = self.dbag['config'][0]['remove_rules']
        stat_rules = self.dbag['config'][0]['stat_rules']
        self._configure_firewall(add_rules, remove_rules, stat_rules)

        udp_rules = self.dbag['config'][0].get('udp_rules', [])
        self._configure_udp(udp_rules)

    def _configure_firewall(self, add_rules, remove_rules, stat_rules):
        firewall = self.config.get_fw()

        logging.debug("CsLoadBalancer:: configuring firewall. Add rules ==> %s" % add_rules)
        logging.debug("CsLoadBalancer:: configuring firewall. Remove rules ==> %s" % remove_rules)
        logging.debug("CsLoadBalancer:: configuring firewall. Stat rules ==> %s" % stat_rules)

        for rules in add_rules:
            path = rules.split(':')
            ip = path[0]
            port = path[1]
            firewall.append(["filter", "", "-A INPUT -p tcp -m tcp -d %s --dport %s -m state --state NEW -j ACCEPT" % (ip, port)])

        for rules in remove_rules:
            path = rules.split(':')
            ip = path[0]
            port = path[1]
            firewall.append(["filter", "", "-D INPUT -p tcp -m tcp -d %s --dport %s -m state --state NEW -j ACCEPT" % (ip, port)])

        for rules in stat_rules:
            path = rules.split(':')
            ip = path[0]
            port = path[1]
            firewall.append(["filter", "", "-A INPUT -p tcp -m tcp -d %s --dport %s -m state --state NEW -j ACCEPT" % (ip, port)])

    def _configure_udp(self, udp_rules):
        """ UDP rules are served by IPVS in NAT mode: the router is the gateway of the
        backends, so their replies come back through it and get the public address back. """
        firewall = self.config.get_fw()
        wanted = {}
        for rule in udp_rules:
            vip = "%s:%s" % (rule['source_ip'], rule['source_port'])
            wanted[vip] = rule
            cidrs = [c for c in rule.get('cidrs', []) if c and c != "0.0.0.0/0"]
            for cidr in cidrs or [None]:
                source = "-s %s " % cidr if cidr else ""
                firewall.append(["filter", "", "-A INPUT %s-p udp -m udp -d %s --dport %s -m state --state NEW -j ACCEPT" %
                                 (source, rule['source_ip'], rule['source_port'])])

        current_services, current_dests = self._get_ipvs_udp()
        if not wanted and not current_services:
            return

        CsHelper.execute("modprobe ip_vs")
        # let netfilter track IPVS connections, so the replies pass the router's stateful rules
        CsHelper.execute("sysctl -w net.ipv4.vs.conntrack=1")
        CsHelper.execute("sysctl -w net.ipv4.vs.expire_nodest_conn=1")

        for vip in current_services:
            if vip not in wanted:
                logging.info("CsLoadBalancer:: removing UDP service %s" % vip)
                CsHelper.execute("ipvsadm -D -u %s" % vip)

        for vip, rule in wanted.items():
            options = "-s %s" % rule['scheduler']
            if int(rule.get('persistence', 0)) > 0:
                options += " -p %s" % rule['persistence']
            if vip not in current_services:
                logging.info("CsLoadBalancer:: adding UDP service %s %s" % (vip, options))
                CsHelper.execute("ipvsadm -A -u %s %s" % (vip, options))
            elif current_services[vip] != (rule['scheduler'], int(rule.get('persistence', 0))):
                logging.info("CsLoadBalancer:: changing UDP service %s to %s" % (vip, options))
                CsHelper.execute("ipvsadm -E -u %s %s" % (vip, options))

            dests = set(rule.get('destinations', []))
            have = current_dests.get(vip, set())
            for dest in have - dests:
                CsHelper.execute("ipvsadm -d -u %s -r %s" % (vip, dest))
            for dest in dests - have:
                CsHelper.execute("ipvsadm -a -u %s -r %s -m" % (vip, dest))

    def _get_ipvs_udp(self):
        """ Returns the UDP services in IPVS as {vip: (scheduler, persistence)} and {vip: set(destinations)} """
        services = {}
        dests = {}
        if not os.path.exists("/proc/net/ip_vs"):
            return services, dests
        for line in CsHelper.execute("ipvsadm-save -n"):
            m = IPVS_SERVICE_RE.match(line)
            if m:
                services[m.group(1)] = (m.group(2), int(m.group(3) or 0))
                dests.setdefault(m.group(1), set())
                continue
            m = IPVS_DEST_RE.match(line)
            if m:
                dests.setdefault(m.group(1), set()).add(m.group(2))
        return services, dests

    def _create_pem_for_sslcert(self, ssl_certs):
        logging.debug("CsLoadBalancer:: creating new pem files in %s and cleaning up it" % SSL_CERTS_DIR)
        if not os.path.exists(SSL_CERTS_DIR):
            CsHelper.execute("mkdir -p %s" % SSL_CERTS_DIR)
        cert_names = []
        for cert in ssl_certs:
            cert_names.append(cert['name'] + ".pem")
            file = CsFile("%s/%s.pem" % (SSL_CERTS_DIR, cert['name']))
            file.empty()
            file.add("%s\n" % cert['cert'].replace("\r\n", "\n"))
            if 'chain' in cert.keys():
                file.add("%s\n" % cert['chain'].replace("\r\n", "\n"))
            file.add("%s\n" % cert['key'].replace("\r\n", "\n"))
            file.commit()
        for f in listdir(SSL_CERTS_DIR):
            if f not in cert_names:
                CsHelper.execute("rm -rf %s/%s" % (SSL_CERTS_DIR, f))

    def _verify_haproxy_config(self, config):
        ret = CsHelper.execute2("haproxy -c -f %s" % config)
        if ret.returncode:
            stdout, stderr = ret.communicate()
            logging.error("haproxy config is invalid with error: %s" % stderr)
            return stderr
        return ""
