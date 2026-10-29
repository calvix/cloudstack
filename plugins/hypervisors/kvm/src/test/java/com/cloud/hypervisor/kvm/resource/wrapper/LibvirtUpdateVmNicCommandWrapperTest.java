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
package com.cloud.hypervisor.kvm.resource.wrapper;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.UpdateVmNicCommand;
import com.cloud.hypervisor.kvm.resource.BridgeVifDriver;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef;
import com.cloud.hypervisor.kvm.resource.LibvirtVMDef.InterfaceDef;
import com.cloud.hypervisor.kvm.resource.OvsVifDriver;
import com.cloud.network.Networks.TrafficType;

@RunWith(MockitoJUnitRunner.class)
public class LibvirtUpdateVmNicCommandWrapperTest {

    private static final String VM_NAME = "r-50-VM";
    private static final String MAC = "1e:00:00:00:00:01";

    @Mock
    private LibvirtComputingResource libvirtComputingResource;
    @Mock
    private LibvirtUtilitiesHelper libvirtUtilitiesHelper;
    @Mock
    private Connect connect;
    @Mock
    private Domain domain;

    private InterfaceDef nic;

    @Before
    public void setUp() throws LibvirtException {
        LibvirtVMDef.setGlobalLibvirtVersion(6_003_000);
        nic = new InterfaceDef();
        nic.defBridgeNet("cloudbr1", "vnet0", MAC, InterfaceDef.NicModel.VIRTIO, 125000);
        when(libvirtComputingResource.getLibvirtUtilitiesHelper()).thenReturn(libvirtUtilitiesHelper);
        when(libvirtUtilitiesHelper.getConnectionByVmName(VM_NAME)).thenReturn(connect);
        when(libvirtComputingResource.getDomain(connect, VM_NAME)).thenReturn(domain);
        when(libvirtComputingResource.getInterface(connect, VM_NAME, MAC)).thenReturn(nic);
    }

    private void nicOfTheBridgeVifDriver() {
        when(libvirtComputingResource.getVifDriver(TrafficType.Public, "cloudbr1")).thenReturn(Mockito.mock(BridgeVifDriver.class));
    }

    private String updatedInterfaceXml(UpdateVmNicCommand command) throws LibvirtException {
        if (command.getNetworkRateMbps() != null) {
            nicOfTheBridgeVifDriver();
        }
        Answer answer = new LibvirtUpdateVmNicCommandWrapper().execute(command, libvirtComputingResource);
        assertTrue(answer.getResult());
        ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
        verify(domain).updateDeviceFlags(xml.capture(), Mockito.eq(Domain.DeviceModifyFlags.LIVE));
        return xml.getValue();
    }

    @Test
    public void networkRateIsSetInKilobytesPerSecond() throws LibvirtException {
        String xml = updatedInterfaceXml(new UpdateVmNicCommand(MAC, VM_NAME, null, 2000));

        assertTrue(xml.contains("<inbound average='250000' peak='250000'/>"));
        assertTrue(xml.contains("<outbound average='250000' peak='250000'/>"));
    }

    @Test
    public void networkRateZeroRemovesTheLimit() throws LibvirtException {
        String xml = updatedInterfaceXml(new UpdateVmNicCommand(MAC, VM_NAME, null, 0));

        assertFalse(xml.contains("<bandwidth>"));
    }

    @Test
    public void networkRateChangeKeepsTheLinkState() throws LibvirtException {
        nic.setLinkStateUp(false);

        String xml = updatedInterfaceXml(new UpdateVmNicCommand(MAC, VM_NAME, null, 2000));

        assertTrue(xml.contains("<link state='down'/>"));
    }

    @Test
    public void networkRateIsNotChangedLiveOnANicOfAnotherVifDriver() throws LibvirtException {
        // the parsed NIC lacks what such drivers add, like an Open vSwitch port and its VLAN tag
        when(libvirtComputingResource.getVifDriver(TrafficType.Public, "cloudbr1")).thenReturn(Mockito.mock(OvsVifDriver.class));

        Answer answer = new LibvirtUpdateVmNicCommandWrapper().execute(new UpdateVmNicCommand(MAC, VM_NAME, null, 2000), libvirtComputingResource);

        assertFalse(answer.getResult());
        verify(domain, never()).updateDeviceFlags(anyString(), Mockito.anyInt());
    }

    @Test
    public void linkStateChangeKeepsTheNetworkRate() throws LibvirtException {
        String xml = updatedInterfaceXml(new UpdateVmNicCommand(MAC, VM_NAME, false));

        assertTrue(xml.contains("<link state='down'/>"));
        assertTrue(xml.contains("<inbound average='125000' peak='125000'/>"));
    }
}
