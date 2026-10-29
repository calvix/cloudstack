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
package com.cloud.agent.api;

public class UpdateVmNicCommand extends Command {

    String nicMacAddress;
    String instanceName;
    Boolean enabled;
    Integer networkRateMbps;

    @Override
    public boolean executeInSequence() {
        return true;
    }

    protected UpdateVmNicCommand() {
    }

    public UpdateVmNicCommand(String nicMacAdderss, String instanceName, Boolean enabled) {
        this.nicMacAddress = nicMacAdderss;
        this.instanceName = instanceName;
        this.enabled = enabled;
    }

    /**
     * @param enabled the NIC's link state, or null to leave it
     * @param networkRateMbps the NIC's rate in Mbps (0 = unlimited), or null to leave it
     */
    public UpdateVmNicCommand(String nicMacAddress, String instanceName, Boolean enabled, Integer networkRateMbps) {
        this(nicMacAddress, instanceName, enabled);
        this.networkRateMbps = networkRateMbps;
    }

    public String getNicMacAddress() {
        return nicMacAddress;
    }

    public String getVmName() {
        return instanceName;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Integer getNetworkRateMbps() {
        return networkRateMbps;
    }
}
