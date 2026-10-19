//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.cloud.agent.resource.virtualnetwork.model;


public class LoadBalancerRule {

    private String[] configuration;
    private String tmpCfgFilePath;
    private String tmpCfgFileName;
    private SslCertEntry[] sslCerts;
    private UdpLbEntry[] udpRules;

    private String[] addRules;
    private String[] removeRules;
    private String[] statRules;

    private String routerIp;

    public static class SslCertEntry {
        private String name;
        private String cert;
        private String key;
        private String chain;
        private String password;

        public SslCertEntry(String name, String cert, String key, String chain, String password) {
            this.name = name;
            this.cert = cert;
            this.key = key;
            this.chain = chain;
            this.password = password;
        }

        public void setName(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public void setCert(String cert) {
            this.cert = cert;
        }
        public String getCert() {
            return cert;
        }
        public void setKey(String key) {
            this.key = key;
        }
        public String getKey() {
            return key;
        }
        public void setChain(String chain) {
            this.chain = chain;
        }
        public String getChain() {
            return chain;
        }
        public void setPassword(String password) {
            this.password = password;
        }
        public String getPassword() {
            return password;
        }
    }

    /**
     * A UDP load balancer rule. The router serves it with IPVS (NAT mode), not haproxy.
     */
    public static class UdpLbEntry {
        private String sourceIp;
        private int sourcePort;
        // IPVS scheduler: rr, lc or sh
        private String scheduler;
        // IPVS persistence timeout in seconds, 0 = none
        private int persistence;
        // "ip:port" of each backend
        private String[] destinations;
        // allowed source CIDRs, empty = everyone
        private String[] cidrs;

        public UdpLbEntry(String sourceIp, int sourcePort, String scheduler, int persistence, String[] destinations, String[] cidrs) {
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.scheduler = scheduler;
            this.persistence = persistence;
            this.destinations = destinations;
            this.cidrs = cidrs;
        }

        public String getSourceIp() {
            return sourceIp;
        }
        public int getSourcePort() {
            return sourcePort;
        }
        public String getScheduler() {
            return scheduler;
        }
        public int getPersistence() {
            return persistence;
        }
        public String[] getDestinations() {
            return destinations;
        }
        public String[] getCidrs() {
            return cidrs;
        }
    }

    public LoadBalancerRule() {
        // Empty constructor for (de)serialization
    }

    public LoadBalancerRule(final String[] configuration, final String tmpCfgFilePath, final String tmpCfgFileName, final String[] addRules, final String[] removeRules, final String[] statRules, final String routerIp) {
        this.configuration = configuration;
        this.tmpCfgFilePath = tmpCfgFilePath;
        this.tmpCfgFileName = tmpCfgFileName;
        this.addRules = addRules;
        this.removeRules = removeRules;
        this.statRules = statRules;
        this.routerIp = routerIp;
    }

    public String[] getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final String[] configuration) {
        this.configuration = configuration;
    }

    public String getTmpCfgFilePath() {
        return tmpCfgFilePath;
    }

    public void setTmpCfgFilePath(final String tmpCfgFilePath) {
        this.tmpCfgFilePath = tmpCfgFilePath;
    }

    public String getTmpCfgFileName() {
        return tmpCfgFileName;
    }

    public void setTmpCfgFileName(final String tmpCfgFileName) {
        this.tmpCfgFileName = tmpCfgFileName;
    }

    public String[] getAddRules() {
        return addRules;
    }

    public void setAddRules(final String[] addRules) {
        this.addRules = addRules;
    }

    public String[] getRemoveRules() {
        return removeRules;
    }

    public void setRemoveRules(final String[] removeRules) {
        this.removeRules = removeRules;
    }

    public String[] getStatRules() {
        return statRules;
    }

    public void setStatRules(final String[] statRules) {
        this.statRules = statRules;
    }

    public String getRouterIp() {
        return routerIp;
    }

    public void setRouterIp(final String routerIp) {
        this.routerIp = routerIp;
    }

    public SslCertEntry[] getSslCerts() {
        return sslCerts;
    }

    public void setSslCerts(final SslCertEntry[] sslCerts) {
        this.sslCerts = sslCerts;
    }

    public UdpLbEntry[] getUdpRules() {
        return udpRules;
    }

    public void setUdpRules(final UdpLbEntry[] udpRules) {
        this.udpRules = udpRules;
    }
}
