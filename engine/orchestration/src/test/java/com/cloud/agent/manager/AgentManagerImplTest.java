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
package com.cloud.agent.manager;

import com.cloud.agent.Listener;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.MigrateAgentConnectionCommand;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.exception.ConnectionException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

public class AgentManagerImplTest {

    private HostDao hostDao;
    private Listener storagePoolMonitor;
    private AgentAttache attache;
    private AgentManagerImpl mgr = Mockito.spy(new AgentManagerImpl());
    private HostVO host;
    private StartupCommand[] cmds;

    @Before
    public void setUp() throws Exception {
        host = new HostVO("some-Uuid");
        host.setDataCenterId(1L);
        cmds = new StartupCommand[]{new StartupRoutingCommand()};
        attache = new ConnectedAgentAttache(null, 1L, "uuid", "kvm-attache", Hypervisor.HypervisorType.KVM, null, false);

        hostDao = Mockito.mock(HostDao.class);
        storagePoolMonitor = Mockito.mock(Listener.class);

        mgr._hostDao = hostDao;
        mgr._hostMonitors = new ArrayList<>();
        mgr._hostMonitors.add(new Pair<>(0, storagePoolMonitor));
    }

    @Test
    public void testNotifyMonitorsOfConnectionNormal() throws ConnectionException {
        Mockito.when(hostDao.findById(Mockito.anyLong())).thenReturn(host);
        Mockito.doNothing().when(storagePoolMonitor).processConnect(Mockito.eq(host), Mockito.eq(cmds[0]), Mockito.eq(false));
        Mockito.doReturn(true).when(mgr).handleDisconnectWithoutInvestigation(Mockito.any(attache.getClass()), Mockito.any(Status.Event.class), Mockito.anyBoolean(), Mockito.anyBoolean());
        Mockito.doReturn(Mockito.mock(Answer.class)).when(mgr).easySend(Mockito.anyLong(), Mockito.any(ReadyCommand.class));
        Mockito.doReturn(true).when(mgr).agentStatusTransitTo(Mockito.eq(host), Mockito.eq(Status.Event.Ready), Mockito.anyLong());

        final AgentAttache agentAttache = mgr.notifyMonitorsOfConnection(attache, cmds, false);
        Assert.assertTrue(agentAttache.isReady()); // Agent is in UP state
    }

    @Test
    public void testNotifyMonitorsOfConnectionWhenStoragePoolConnectionHostFailure() throws ConnectionException {
        ConnectionException connectionException = new ConnectionException(true, "storage pool could not be connected on host");
        Mockito.when(hostDao.findById(Mockito.anyLong())).thenReturn(host);
        Mockito.doThrow(connectionException).when(storagePoolMonitor).processConnect(Mockito.eq(host), Mockito.eq(cmds[0]), Mockito.eq(false));
        Mockito.doReturn(true).when(mgr).handleDisconnectWithoutInvestigation(Mockito.any(attache.getClass()), Mockito.any(Status.Event.class), Mockito.anyBoolean(), Mockito.anyBoolean());
        try {
            mgr.notifyMonitorsOfConnection(attache, cmds, false);
            Assert.fail("Connection Exception was expected");
        } catch (ConnectionException e) {
            Assert.assertEquals(e.getMessage(), connectionException.getMessage());
        }
        Mockito.verify(mgr, Mockito.times(1)).handleDisconnectWithoutInvestigation(Mockito.any(attache.getClass()), Mockito.eq(Status.Event.AgentDisconnected), Mockito.eq(true), Mockito.eq(true));
    }

    @Test
    public void testGetTimeoutWithPositiveTimeout() {
        Commands commands = Mockito.mock(Commands.class);
        int timeout = 30;
        int result = mgr.getTimeout(commands, timeout);

        Assert.assertEquals(30, result);
    }

    @Test
    public void testGetTimeoutWithGranularTimeout() {
        Commands commands = Mockito.mock(Commands.class);
        Mockito.doReturn(50).when(mgr).getTimeoutFromGranularWaitTime(commands);

        int timeout = 0;
        int result = mgr.getTimeout(commands, timeout);

        Assert.assertEquals(50, result);
    }

    @Test
    public void testGetHostSshPortWithHostNull() {
        int hostSshPort = mgr.getHostSshPort(null);
        Assert.assertEquals(22, hostSshPort);
    }

    @Test
    public void testGetHostSshPortWithNonKVMHost() {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.XenServer);
        int hostSshPort = mgr.getHostSshPort(host);
        Assert.assertEquals(22, hostSshPort);
    }

    @Test
    public void testGetHostSshPortWithKVMHostDefaultPort() {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        Mockito.when(host.getClusterId()).thenReturn(1L);
        int hostSshPort = mgr.getHostSshPort(host);
        Assert.assertEquals(22, hostSshPort);
    }

    @Test
    public void testGetHostSshPortWithKVMHostCustomPort() {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        Mockito.when(host.getDetail(Host.HOST_SSH_PORT)).thenReturn(String.valueOf(3922));
        int hostSshPort = mgr.getHostSshPort(host);
        Assert.assertEquals(3922, hostSshPort);
    }

    private HostVO hostWithStatus(Status status) {
        HostVO h = Mockito.mock(HostVO.class);
        Mockito.when(h.getStatus()).thenReturn(status);
        return h;
    }

    @Test
    public void waitWhileAgentHandoffDoesNotWaitForAnUpHost() {
        HostVO up = hostWithStatus(Status.Up);
        Mockito.when(hostDao.findById(1L)).thenReturn(up);
        long start = System.nanoTime();
        mgr.waitWhileAgentHandoff(1L, new Command[]{new MaintainCommand()});
        Assert.assertTrue(System.nanoTime() - start < 1_000_000_000L);
        Mockito.verify(hostDao, Mockito.times(1)).findById(1L);
    }

    @Test
    public void waitWhileAgentHandoffNeverHoldsTheMoveCommand() {
        HostVO rebalancing = hostWithStatus(Status.Rebalancing);
        Mockito.when(hostDao.findById(1L)).thenReturn(rebalancing);
        mgr.waitWhileAgentHandoff(1L, new Command[]{new MigrateAgentConnectionCommand()});
        Mockito.verify(hostDao, Mockito.never()).findById(1L);
    }

    @Test
    public void waitWhileAgentHandoffHoldsThroughRebalancingAndConnectingUntilUp() {
        HostVO rebalancing = hostWithStatus(Status.Rebalancing);
        HostVO connecting = hostWithStatus(Status.Connecting);
        HostVO up = hostWithStatus(Status.Up);
        Mockito.when(hostDao.findById(1L)).thenReturn(rebalancing, rebalancing, connecting, up);
        long start = System.nanoTime();
        mgr.waitWhileAgentHandoff(1L, new Command[]{new MaintainCommand()});
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        Mockito.verify(hostDao, Mockito.times(4)).findById(1L);
        Assert.assertTrue("released only once the host is Up, elapsed " + elapsedMs + " ms", elapsedMs >= 1000 && elapsedMs < 10_000);
    }

    @Test
    public void easySendPassesARebalancingHostToSend() throws Exception {
        Answer answer = Mockito.mock(Answer.class);
        HostVO rebalancing = hostWithStatus(Status.Rebalancing);
        Mockito.when(hostDao.findById(1L)).thenReturn(rebalancing);
        Mockito.doReturn(answer).when(mgr).send(Mockito.eq(1L), Mockito.any(Command.class));
        Assert.assertSame(answer, mgr.easySend(1L, new MaintainCommand()));
    }

    @Test
    public void easySendStillRefusesADisconnectedHost() throws Exception {
        HostVO disconnected = hostWithStatus(Status.Disconnected);
        Mockito.when(hostDao.findById(1L)).thenReturn(disconnected);
        Assert.assertNull(mgr.easySend(1L, new MaintainCommand()));
        Mockito.verify(mgr, Mockito.never()).send(Mockito.anyLong(), Mockito.any(Command.class));
    }

    @Test
    public void isAgentIdleOnlyWhileNoCommandWaitsForItsAnswer() {
        Mockito.doReturn(attache).when(mgr).findAttache(1L);
        Assert.assertTrue(mgr.isAgentIdle(1L));
        Listener waiting = Mockito.mock(Listener.class);
        Mockito.when(waiting.isRecurring()).thenReturn(false);
        attache.registerListener(42L, waiting);
        Assert.assertFalse(mgr.isAgentIdle(1L));
        attache.unregisterListener(42L);
        Assert.assertTrue(mgr.isAgentIdle(1L));
        Mockito.doReturn(null).when(mgr).findAttache(2L);
        Assert.assertTrue(mgr.isAgentIdle(2L));
    }
}
