/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.network.vpc;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.UpdateNetworkCommand;
import com.cloud.agent.api.to.IpAddressTO;
import com.cloud.agent.manager.Commands;
import com.cloud.alert.AlertManager;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.IpAddressManager;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.element.NetworkElement;
import com.cloud.network.element.VpcProvider;
import com.cloud.network.router.CommandSetupHelper;
import com.cloud.network.router.NetworkHelper;
import com.cloud.network.router.VirtualRouter;
import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.network.vpc.dao.VpcDao;
import com.cloud.network.vpc.dao.VpcOfferingDao;
import com.cloud.network.vpc.dao.VpcOfferingServiceMapDao;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.NetworkOfferingServiceMapVO;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.resourcelimit.CheckedReservation;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.DomainRouterVO;
import com.cloud.agent.AgentManager;
import com.cloud.agent.api.UpdateVmNicCommand;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.NetworkVO;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicVO;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.resourcedetail.VpcDetailVO;
import org.apache.cloudstack.resourcedetail.dao.VpcDetailsDao;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.NicDao;
import org.apache.cloudstack.acl.SecurityChecker;
import org.apache.cloudstack.api.command.admin.vpc.CreateVPCOfferingCmd;
import org.apache.cloudstack.api.command.user.vpc.UpdateVPCCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.extension.Extension;
import org.apache.cloudstack.extension.ExtensionHelper;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.network.Ipv4GuestSubnetNetworkMap;
import org.apache.cloudstack.network.RoutedIpv4Manager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class VpcManagerImplTest {

    @Mock
    VpcOfferingServiceMapDao vpcOffSvcMapDao;
    @Mock
    VpcDao vpcDao;
    @Mock
    NetworkOrchestrationService networkMgr;
    @Mock
    AccountManager accountManager;
    @Mock
    ResourceLimitService resourceLimitService;
    @Mock
    VpcOfferingDao vpcOfferingDao;
    @Mock
    DataCenterDao dataCenterDao;
    @Mock
    VpcOfferingServiceMapDao vpcOfferingServiceMapDao;
    VpcManagerImpl manager;
    @Mock
    EntityManager entityMgr;
    @Mock
    NetworkDao networkDao;
    @Mock
    NetworkACLDao networkACLDaoMock;
    @Mock
    NetworkModel networkModel;
    @Mock
    NetworkOfferingServiceMapDao networkOfferingServiceMapDao;
    @Mock
    private CallContext callContextMock;
    @Mock
    IPAddressDao ipAddressDao;
    @Mock
    DomainRouterDao routerDao;
    @Mock
    CommandSetupHelper commandSetupHelper;
    @Mock
    NetworkHelper networkHelper;
    @Mock
    VlanDao vlanDao;
    @Mock
    NicDao nicDao;
    @Mock
    AlertManager alertManager;
    @Mock
    NetworkService networkServiceMock;
    @Mock
    FirewallRulesDao firewallDao;
    @Mock
    NetworkACLVO networkACLVOMock;
    @Mock
    RoutedIpv4Manager routedIpv4Manager;
    @Mock
    IpAddressManager ipAddressManager;
    @Mock
    VpcDetailsDao vpcDetailsDao;
    @Mock
    AgentManager agentManager;

    public static final long ACCOUNT_ID = 1;
    private AccountVO account;
    private UserVO user;

    private static final String IP4_GATEWAY = "10.0.16.1";
    private static final String IP4_NETMASK = "255.255.255.0";
    private static final String IP6_GATEWAY = "fd17:ac56:1234:2000::1";
    private static final String IP6_CIDR = "fd17:ac56:1234:2000::/64";

    final String[] ip4Dns = {"5.5.5.5", "6.6.6.6"};
    final String[] ip6Dns = {"2001:4860:4860::5555", "2001:4860:4860::6666"};
    final String ip4Cidr = "172.16.0.0/22";
    final Long zoneId = 1L;
    final Long vpcOfferingId = 1L;
    final Long vpcOwnerId = 1L;
    final String vpcName = "Test-VPC";
    final String vpcDomain = "domain";
    final Long aclId = 1L;
    final Long differentVpcAclId = 3L;
    final Long vpcId = 1L;

    private AutoCloseable closeable;

    private VpcOfferingVO vpcOfferingVO;

    private void registerCallContext() {
        account = new AccountVO("testaccount", 1L, "networkdomain", Account.Type.NORMAL, "uuid");
        account.setId(ACCOUNT_ID);
        user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone",
                UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
    }

    @Before
    public void setup() throws NoSuchFieldException, IllegalAccessException {
        closeable = MockitoAnnotations.openMocks(this);
        manager = new VpcManagerImpl();
        manager._vpcOffSvcMapDao = vpcOfferingServiceMapDao;
        manager.vpcDao = vpcDao;
        manager._ntwkMgr = networkMgr;
        manager._accountMgr = accountManager;
        manager._entityMgr = entityMgr;
        manager._ntwkDao = networkDao;
        manager._ntwkModel = networkModel;
        manager._ntwkOffServiceDao = networkOfferingServiceMapDao;
        manager._ipAddressDao = ipAddressDao;
        manager.routerDao = routerDao;
        manager.commandSetupHelper = commandSetupHelper;
        manager.networkHelper = networkHelper;
        manager._vlanDao = vlanDao;
        manager.nicDao = nicDao;
        manager.alertManager = alertManager;
        manager._resourceLimitMgr = resourceLimitService;
        manager._vpcOffDao = vpcOfferingDao;
        manager._dcDao = dataCenterDao;
        manager._ntwkSvc = networkServiceMock;
        manager._firewallDao = firewallDao;
        manager._networkAclDao = networkACLDaoMock;
        manager.routedIpv4Manager = routedIpv4Manager;
        manager._ipAddrMgr = ipAddressManager;
        manager.vpcDetailsDao = vpcDetailsDao;
        manager.agentManager = agentManager;
        CallContext.register(Mockito.mock(User.class), Mockito.mock(Account.class));
        registerCallContext();
        overrideDefaultConfigValue(NetworkService.AllowUsersToSpecifyVRMtu, "_defaultValue", "false");
    }

    @After
    public void tearDown() throws Exception {
        CallContext.unregister();
        closeable.close();
    }

    private void overrideDefaultConfigValue(final ConfigKey configKey, final String name,
            final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field f = ConfigKey.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(configKey, o);
    }

    @Test
    public void getVpcOffSvcProvidersMapForEmptyServiceTest() {
        long vpcOffId = 1L;
        VpcOfferingServiceMapVO svcMap = mock(VpcOfferingServiceMapVO.class);
        Mockito.when(svcMap.getService()).thenReturn(Service.SourceNat.getName());
        Mockito.when(svcMap.getProvider()).thenReturn(Provider.VPCVirtualRouter.getName());
        Mockito.when(networkModel.resolveProvider(Provider.VPCVirtualRouter.getName()))
               .thenReturn(Provider.VPCVirtualRouter);
        List<VpcOfferingServiceMapVO> list = new ArrayList<VpcOfferingServiceMapVO>();
        list.add(svcMap);
        Mockito.when(manager._vpcOffSvcMapDao.listByVpcOffId(vpcOffId)).thenReturn(list);

        Map<Service, Set<Provider>> map = manager.getVpcOffSvcProvidersMap(vpcOffId);

        assertNotNull(map);
        assertEquals(map.size(), 1);
        assertTrue(map.containsKey(Service.SourceNat));
        assertTrue(map.get(Service.SourceNat).contains(Provider.VPCVirtualRouter));
    }

    protected Map<String, String> createFakeCapabilityInputMap() {
        Map<String, String> map = new HashMap<String, String>();
        map.put(VpcManagerImpl.CAPABILITYVALUE, VpcManagerImpl.TRUE_VALUE);
        map.put(VpcManagerImpl.CAPABILITYTYPE, Network.Capability.SupportedProtocols.getName());
        map.put(VpcManagerImpl.SERVICE, "");
        return map;
    }

    @Test
    public void testIsVpcOfferingForRegionLevelVpc() throws Exception {
        // Prepare
        Map<String, Map<String, String>> serviceCapabilitystList = new HashMap<>();
        // First add some other capabilities to be ignored
        serviceCapabilitystList.put("", createFakeCapabilityInputMap());

        Map<String, String> servicePair = new HashMap<>();
        servicePair.put(VpcManagerImpl.SERVICE, Service.Connectivity.getName());
        servicePair.put(VpcManagerImpl.CAPABILITYTYPE, Network.Capability.RegionLevelVpc.getName());
        servicePair.put(VpcManagerImpl.CAPABILITYVALUE, VpcManagerImpl.TRUE_VALUE);
        serviceCapabilitystList.put("", servicePair);


        // Execute
        boolean result = ReflectionTestUtils.invokeMethod(this.manager, "isVpcOfferingForRegionLevelVpc",
                serviceCapabilitystList); //, Network.Capability.RedundantRouter.getName(), Service.SourceNat);

        // Assert
        assertEquals("VpcOffering should be created for Region Level Vpc", true, result);
    }

    @Test
    public void testIsVpcOfferingForRegionLevelVpcFalse() throws Exception {
        // Prepare
        Map<String, Map<String, String>> serviceCapabilitystList = new HashMap<>();
        // First add some other capabilities to be ignored
        serviceCapabilitystList.put("", createFakeCapabilityInputMap());
        serviceCapabilitystList.put("", createFakeCapabilityInputMap());

        // Execute
        boolean result = ReflectionTestUtils.invokeMethod(this.manager, "isVpcOfferingForRegionLevelVpc",
                serviceCapabilitystList);

        // Assert
        assertEquals("VpcOffering should be created NOT for Region Level Vpc", false, result);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testCheckCapabilityPerServiceProviderFail() {
        // Prepare
        final Map<Capability, String> capabilities = new HashMap<>();
        capabilities.put(Capability.RegionLevelVpc, "");
        capabilities.put(Capability.DistributedRouter, "");
        Set<Network.Provider> providers = this.prepareVpcManagerForCheckingCapabilityPerService(Service.Connectivity, capabilities);

        // Execute
        this.manager.checkCapabilityPerServiceProvider(providers, Capability.RedundantRouter, Service.SourceNat);
    }

    @Test
    public void testCheckCapabilityPerServiceProvider() {
        // Prepare
        final Map<Capability, String> capabilities = new HashMap<>();
        capabilities.put(Capability.RegionLevelVpc, "");
        capabilities.put(Capability.DistributedRouter, "");
        Set<Network.Provider> providers = this.prepareVpcManagerForCheckingCapabilityPerService(Service.Connectivity, capabilities);

        // Execute
        this.manager.checkCapabilityPerServiceProvider(providers, Capability.DistributedRouter, Service.Connectivity);
        this.manager.checkCapabilityPerServiceProvider(providers, Capability.RegionLevelVpc, Service.Connectivity);
    }

    protected Set<Network.Provider> prepareVpcManagerForCheckingCapabilityPerService(Service service, Map<Capability, String> capabilities) {
        final Set<Network.Provider> providers = new HashSet<>();
        providers.add(Provider.VPCVirtualRouter);
        final Network.Capability capability = Capability.DistributedRouter;
        final boolean regionLevel = true;
        final boolean distributedRouter = true;
        final NetworkElement nwElement1 = mock(NetworkElement.class);
        this.manager._ntwkModel = mock(NetworkModel.class);
        Mockito.when(this.manager._ntwkModel.getElementImplementingProvider(Provider.VPCVirtualRouter.getName()))
                .thenReturn(nwElement1);
        final Map<Service, Map<Network.Capability, String>> capabilitiesService1 = new HashMap<>();
        Mockito.when(nwElement1.getCapabilities()).thenReturn(capabilitiesService1);
        capabilities.put(Capability.RegionLevelVpc, "");
        capabilities.put(Capability.DistributedRouter, "");
        capabilitiesService1.put(service, capabilities);

        return providers;
    }

    @Test
    public void testCreateVpcNetwork() throws InsufficientCapacityException, ResourceAllocationException {
        final long VPC_ID = 201L;
        manager._maxNetworks = 3;
        VpcVO vpcMockVO = Mockito.mock(VpcVO.class);
        Vpc vpcMock = Mockito.mock(Vpc.class);
        Account accountMock = Mockito.mock(Account.class);
        PhysicalNetwork physicalNetwork = Mockito.mock(PhysicalNetwork.class);
        NetworkOffering offering = Mockito.mock(NetworkOffering.class);
        List<Network.Service> services = new ArrayList<>();
        services.add(Service.SourceNat);
        List<NetworkOfferingServiceMapVO> serviceMap = new ArrayList<>();

        Mockito.when(manager.getActiveVpc(anyLong())).thenReturn(vpcMock);
        doNothing().when(accountManager).checkAccess(any(Account.class), nullable(SecurityChecker.AccessType.class), anyBoolean(), any(Vpc.class));
        Mockito.when(vpcMock.isRegionLevelVpc()).thenReturn(true);
        Mockito.when(entityMgr.findById(NetworkOffering.class, 1L)).thenReturn(offering);
        Mockito.when(vpcMock.getId()).thenReturn(VPC_ID);
        Mockito.when(vpcDao.acquireInLockTable(VPC_ID)).thenReturn(vpcMockVO);
        Mockito.when(networkDao.countVpcNetworks(anyLong())).thenReturn(1L);
        Mockito.when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        Mockito.when(networkModel.listNetworkOfferingServices(anyLong())).thenReturn(services);
        Mockito.when(networkOfferingServiceMapDao.listByNetworkOfferingId(anyLong())).thenReturn(serviceMap);
        Mockito.when(vpcMock.getCidr()).thenReturn("10.0.0.0/8");
        Mockito.when(vpcMock.getNetworkDomain()).thenReturn("cs1cloud.internal");

        manager.validateNewVpcGuestNetwork("10.10.10.0/24", "10.10.10.1", accountMock, vpcMock, "cs1cloud.internal");
        manager.validateNtwkOffForNtwkInVpc(2L, 1, "10.10.10.0/24", "111-", vpcMock, "10.1.1.1", new AccountVO(), null);
        manager.validateNtwkOffForVpc(offering, services);
        manager.createVpcGuestNetwork(1L, "vpcNet1", "vpc tier 1", null,
                "10.10.10.0/24", null, null, accountMock, null, physicalNetwork,
                1L, null, null, 1L, null, accountMock,
                true, null, null, null, null, null, null, null, new Pair<>(1000, 1000), null);

        Mockito.verify(networkMgr, times(1)).createGuestNetwork(1L, "vpcNet1", "vpc tier 1", null,
                "10.10.10.0/24", null, false, "cs1cloud.internal", accountMock, null,
                physicalNetwork, zoneId, null, null, 1L, null, null,
                true, null, null, null, null,
                null, null, null, null, null, new Pair<>(1000, 1000), null);
    }

    @Test
    public void testUpdateVpcNetwork() throws ResourceUnavailableException, InsufficientCapacityException {
        long vpcId = 1L;
        Integer publicMtu = 1450;
        String sourceNatIp = "1.2.3.4";
        Account accountMock = Mockito.mock(Account.class);
        VpcVO vpcVO = new VpcVO();

        Answer answer = Mockito.mock(Answer.class);
        Mockito.when(answer.getResult()).thenReturn(true);
        VirtualRouter routerMock = Mockito.mock(VirtualRouter.class);
        List<IPAddressVO> ipAddresses = new ArrayList<>();
        IPAddressVO ipAddressVO = Mockito.mock(IPAddressVO.class);
        Mockito.when(ipAddressVO.getAddress()).thenReturn(Mockito.mock(Ip.class));
        ipAddresses.add(ipAddressVO);
        List<IpAddressTO> ips = new ArrayList<>();
        List<DomainRouterVO> routers = new ArrayList<>();
        DomainRouterVO router = Mockito.mock(DomainRouterVO.class);
        routers.add(router);

        IpAddressTO[] ipsToSend = ips.toArray(new IpAddressTO[0]);

        Mockito.when(vpcDao.findById(vpcId)).thenReturn(vpcVO);
        Mockito.when(vpcDao.createForUpdate(anyLong())).thenReturn(vpcVO);
        Mockito.when(ipAddressDao.listByAssociatedVpc(anyLong(), nullable(Boolean.class))).thenReturn(ipAddresses);
        Mockito.when(routerDao.listByVpcId(anyLong())).thenReturn(routers);
        VlanVO vlanVO = Mockito.mock(VlanVO.class);
        Mockito.when(vlanVO.getVlanNetmask()).thenReturn("netmask");
        Mockito.when(vlanDao.findById(anyLong())).thenReturn(vlanVO);
        Mockito.doAnswer((org.mockito.stubbing.Answer<Void>) invocation -> {
            Commands commands = (Commands) invocation.getArguments()[2];
            commands.addCommand("updateNetwork", new UpdateNetworkCommand(ipsToSend));
            return null;
        }).when(commandSetupHelper).setupUpdateNetworkCommands(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer((org.mockito.stubbing.Answer<Boolean>) invocation -> {
            Commands commands = (Commands) invocation.getArguments()[1];
            commands.setAnswers(new Answer[]{answer});
            return true;
        }).when(networkHelper).sendCommandsToRouter(Mockito.any(), Mockito.any());
        Mockito.when(vpcDao.update(vpcId, vpcVO)).thenReturn(true);

        UpdateVPCCmd cmd = Mockito.mock(UpdateVPCCmd.class);
        Mockito.when(cmd.getId()).thenReturn(vpcId);
        Mockito.when(cmd.getVpcName()).thenReturn(null);
        Mockito.when(cmd.getDisplayText()).thenReturn(null);
        Mockito.when(cmd.getCustomId()).thenReturn(null);
        Mockito.when(cmd.isDisplayVpc()).thenReturn(true);
        Mockito.when(cmd.getPublicMtu()).thenReturn(publicMtu);
        Mockito.when(cmd.getSourceNatIP()).thenReturn(sourceNatIp);
        Mockito.when(cmd.getNetworkRate()).thenReturn(null);

        manager.updateVpc(cmd);
        Assert.assertEquals(publicMtu, vpcVO.getPublicMtu());
    }

    @Test
    public void verifySourceNatIp() {
        String sourceNatIp = "1.2.3.4";
        VpcVO vpcVO = Mockito.mock(VpcVO.class); //new VpcVO(1l, "vpc", null, 10l, 1l, 1l, "10.1.0.0/16", null, false, false, false, null, null, null, null);
        Mockito.when(vpcVO.getId()).thenReturn(1l);
        IPAddressVO requestedIp = Mockito.mock(IPAddressVO.class);//new IPAddressVO(new Ip(sourceNatIp), 1l, 1l, 1l, true);
        Mockito.when(ipAddressDao.findByIp(sourceNatIp)).thenReturn(requestedIp);
        Mockito.when(requestedIp.getVpcId()).thenReturn(1l);
        Mockito.when(requestedIp.getVpcId()).thenReturn(1l);
        Assert.assertNull(manager.validateSourceNatip(vpcVO, null));
        Assert.assertEquals(requestedIp, manager.validateSourceNatip(vpcVO, sourceNatIp));
    }

    @Test
    public void testUpdatePublicMtuToGreaterThanThreshold() {
        Integer publicMtu = 2500;
        Integer expectedMtu = 1500;

        VpcVO vpcVO = new VpcVO();

        Integer mtu = manager.validateMtu(vpcVO, publicMtu);
        Assert.assertEquals(expectedMtu, mtu);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testDisabledConfigCreateIpv6VpcOffering() {
        CreateVPCOfferingCmd cmd = Mockito.mock(CreateVPCOfferingCmd.class);
        Mockito.when(cmd.getInternetProtocol()).thenReturn(NetUtils.InternetProtocol.DualStack.toString());
        doNothing().when(networkServiceMock).validateIfServiceOfferingIsActiveAndSystemVmTypeIsDomainRouter(Mockito.any());
        manager.createVpcOffering(cmd);
    }

    private void mockVpcDnsResources(boolean supportDnsService, boolean isIpv6) {
        Mockito.when(accountManager.getAccount(vpcOwnerId)).thenReturn(account);
        vpcOfferingVO = Mockito.mock(VpcOfferingVO.class);
        Mockito.when(vpcOfferingVO.getId()).thenReturn(vpcOfferingId);
        Mockito.when(vpcOfferingVO.getState()).thenReturn(VpcOffering.State.Enabled);
        Mockito.when(vpcOfferingDao.findById(vpcOfferingId)).thenReturn(vpcOfferingVO);
        DataCenterVO dataCenterVO = Mockito.mock(DataCenterVO.class);
        Mockito.when(dataCenterDao.findById(zoneId)).thenReturn(dataCenterVO);
        doNothing().when(accountManager).checkAccess(account, vpcOfferingVO, dataCenterVO);
        Mockito.when(vpcOfferingServiceMapDao.areServicesSupportedByVpcOffering(vpcOfferingId, new Service[]{Service.Dns})).thenReturn(supportDnsService);
        Mockito.when(vpcOfferingDao.isIpv6Supported(vpcOfferingId)).thenReturn(isIpv6);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testCreateVpcDnsOfferingServiceFailure() {
        mockVpcDnsResources(false, false);
        try {
            manager.createVpc(zoneId, vpcOfferingId, vpcOwnerId, vpcName, vpcName, ip4Cidr, vpcDomain,
                    ip4Dns[0], null, null, null, true, 1500, null, null, null, false, true);
        } catch (ResourceAllocationException e) {
            Assert.fail(String.format("failure with exception: %s", e.getMessage()));
        }
    }

    @Test(expected = InvalidParameterValueException.class)
    public void testCreateVpcDnsIpv6OfferingFailure() {
        mockVpcDnsResources(true, false);
        try {
            manager.createVpc(zoneId, vpcOfferingId, vpcOwnerId, vpcName, vpcName, ip4Cidr, vpcDomain,
                    ip4Dns[0], ip4Dns[1], ip6Dns[0], null, true, 1500, null, null, null, false, true);
        } catch (ResourceAllocationException e) {
            Assert.fail(String.format("failure with exception: %s", e.getMessage()));
        }
    }

    @Test
    public void testCreateVpc() {
        mockVpcDnsResources(true, false);
        VpcVO vpc = Mockito.mock(VpcVO.class);
        Mockito.when(vpcDao.persist(any(), anyMap())).thenReturn(vpc);
        Mockito.when(vpc.getUuid()).thenReturn("uuid");
        try (MockedConstruction<CheckedReservation> mockCheckedReservation = Mockito.mockConstruction(CheckedReservation.class)) {
            manager.createVpc(zoneId, vpcOfferingId, vpcOwnerId, vpcName, vpcName, ip4Cidr, vpcDomain,
                    ip4Dns[0], ip4Dns[1], null, null, true, 1500, null, null, null, false, true);
        } catch (ResourceAllocationException e) {
            Assert.fail(String.format("failure with exception: %s", e.getMessage()));
        }
    }

    @Test
    public void testCreateRoutedVpc() {
        mockVpcDnsResources(true, false);
        VpcVO vpc = Mockito.mock(VpcVO.class);
        Mockito.when(vpcDao.persist(any(), anyMap())).thenReturn(vpc);
        Mockito.when(vpc.getUuid()).thenReturn("uuid");
        doReturn(true).when(routedIpv4Manager).isRoutedVpc(any());
        doNothing().when(routedIpv4Manager).getOrCreateIpv4SubnetForVpc(any(), anyString());
        try (MockedConstruction<CheckedReservation> mockCheckedReservation = Mockito.mockConstruction(CheckedReservation.class)) {
            manager.createVpc(zoneId, vpcOfferingId, vpcOwnerId, vpcName, vpcName, ip4Cidr, vpcDomain,
                    ip4Dns[0], ip4Dns[1], null, null, true, 1500, null, null, null, false, true);
        } catch (ResourceAllocationException e) {
            Assert.fail(String.format("failure with exception: %s", e.getMessage()));
        }

        verify(routedIpv4Manager).getOrCreateIpv4SubnetForVpc(any(), anyString());
    }

    @Test
    public void testCreateRoutedVpcWithDynamicRouting() {
        mockVpcDnsResources(true, false);
        VpcVO vpc = Mockito.mock(VpcVO.class);
        Mockito.when(vpcDao.persist(any(), anyMap())).thenReturn(vpc);
        Mockito.when(vpc.getUuid()).thenReturn("uuid");
        doReturn(true).when(routedIpv4Manager).isRoutedVpc(any());
        doReturn(true).when(routedIpv4Manager).isValidGateway(vpcOfferingVO);
        doReturn(true).when(routedIpv4Manager).isDynamicRoutedVpc(vpcOfferingVO);
        Ipv4GuestSubnetNetworkMap ipv4GuestSubnetNetworkMap = Mockito.mock(Ipv4GuestSubnetNetworkMap.class);
        doReturn(ipv4GuestSubnetNetworkMap).when(routedIpv4Manager).getOrCreateIpv4SubnetForVpc(any(), anyInt());
        List<Long> bgpPeerIds = Arrays.asList(11L, 12L);
        try (MockedConstruction<CheckedReservation> mockCheckedReservation = Mockito.mockConstruction(CheckedReservation.class)) {

            manager.createVpc(zoneId, vpcOfferingId, vpcOwnerId, vpcName, vpcName, null, vpcDomain,
                    ip4Dns[0], ip4Dns[1], null, null, true, 1500, 24, null, bgpPeerIds, false, true);
        } catch (ResourceAllocationException e) {
            Assert.fail(String.format("failure with exception: %s", e.getMessage()));
        }

        verify(routedIpv4Manager).getOrCreateIpv4SubnetForVpc(any(), anyInt());
        verify(routedIpv4Manager).validateBgpPeers(any(), any(), any());
    }

    @Test
    public void validateVpcPrivateGatewayAclIdTestNullAclVoThrowsInvalidParameterValueException() {
        Mockito.doReturn(null).when(networkACLDaoMock).findById(aclId);
        Assert.assertThrows(InvalidParameterValueException.class, () -> manager.validateVpcPrivateGatewayAclId(vpcId, aclId));
    }

    @Test
    public void validateVpcPrivateGatewayTestAclFromDifferentVpcThrowsInvalidParameterValueException() {
        Mockito.doReturn(2L).when(networkACLVOMock).getVpcId();
        Mockito.doReturn(networkACLVOMock).when(networkACLDaoMock).findById(differentVpcAclId);
        Assert.assertThrows(InvalidParameterValueException.class, () -> manager.validateVpcPrivateGatewayAclId(vpcId, differentVpcAclId));
    }

    @Test
    public void testIsNetworkOnVpcEnabledConserveModeIsolatedNetwork() {
        Network network = mock(Network.class);
        Mockito.when(network.getVpcId()).thenReturn(null);
        Assert.assertFalse(manager.isNetworkOnVpcEnabledConserveMode(network));
    }

    @Test
    public void testIsNetworkOnVpcEnabledConserveModeVpcNetworkConserveMode() {
        Network network = mock(Network.class);
        Vpc vpc = mock(Vpc.class);
        VpcOfferingVO vpcOffering = mock(VpcOfferingVO.class);
        long vpcId = 10L;
        long vpcOfferingId = 11L;

        Mockito.when(network.getVpcId()).thenReturn(vpcId);
        Mockito.when(vpcDao.getActiveVpcById(Mockito.eq(vpcId))).thenReturn(vpc);
        Mockito.when(vpc.getVpcOfferingId()).thenReturn(vpcOfferingId);
        Mockito.when(vpcOfferingDao.findById(Mockito.eq(vpcOfferingId))).thenReturn(vpcOffering);
        Mockito.when(vpcOffering.isConserveMode()).thenReturn(true);
        Assert.assertTrue(manager.isNetworkOnVpcEnabledConserveMode(network));
    }

    // -----------------------------------------------------------------------
    // Tests for getVpcElements with extension-backed NetworkOrchestrator
    // -----------------------------------------------------------------------

    @Test
    public void getVpcElementsIncludesExtensionBackedVpcProvider() {
        manager.setVpcElements(null);

        Mockito.when(networkModel.getElementImplementingProvider(Provider.VPCVirtualRouter.getName())).thenReturn(null);
        Mockito.when(networkModel.getElementImplementingProvider(Provider.JuniperContrailVpcRouter.getName())).thenReturn(null);

        Extension ext = mock(Extension.class);
        Mockito.when(ext.getName()).thenReturn("my-vpc-ext");

        ExtensionHelper extHelper = mock(ExtensionHelper.class);
        Mockito.when(extHelper.listExtensionsByType(Extension.Type.NetworkOrchestrator))
                .thenReturn(List.of(ext));
        manager.extensionHelper = extHelper;

        // The element for the extension also implements VpcProvider
        VpcProvider vpcProviderElement = mock(VpcProvider.class);
        Mockito.when(networkModel.getElementImplementingProvider("my-vpc-ext")).thenReturn((NetworkElement) vpcProviderElement);

        List<VpcProvider> result = manager.getVpcElements();
        Assert.assertNotNull(result);
        Assert.assertTrue(result.contains(vpcProviderElement));
    }

    @Test
    public void getVpcElementsReturnsEmptyListWhenNoStaticNorExtensionProviders() {
        manager.setVpcElements(null);

        Mockito.when(networkModel.getElementImplementingProvider(Provider.VPCVirtualRouter.getName())).thenReturn(null);
        Mockito.when(networkModel.getElementImplementingProvider(Provider.JuniperContrailVpcRouter.getName())).thenReturn(null);

        ExtensionHelper extHelper = mock(ExtensionHelper.class);
        Mockito.when(extHelper.listExtensionsByType(Extension.Type.NetworkOrchestrator))
                .thenReturn(Collections.emptyList());
        manager.extensionHelper = extHelper;

        List<VpcProvider> result = manager.getVpcElements();
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    private IPAddressVO vpcIpWithRules(FirewallRuleVO... rules) {
        IPAddressVO ip = Mockito.mock(IPAddressVO.class);
        Mockito.when(ip.getId()).thenReturn(10L);
        Mockito.when(ip.getVpcId()).thenReturn(1L);
        Mockito.when(firewallDao.listByIp(10L)).thenReturn(List.of(rules));
        return ip;
    }

    private FirewallRuleVO ruleOnNetwork(Long networkId) {
        FirewallRuleVO rule = Mockito.mock(FirewallRuleVO.class);
        Mockito.when(rule.getNetworkId()).thenReturn(networkId);
        return rule;
    }

    @Test
    public void vpcFirewallRuleDoesNotKeepTheIpAllocated() {
        Assert.assertFalse(manager.isIpAllocatedToVpc(vpcIpWithRules(ruleOnNetwork(null))));
    }

    @Test
    public void ruleOfANetworkKeepsTheIpAllocated() {
        Assert.assertTrue(manager.isIpAllocatedToVpc(vpcIpWithRules(ruleOnNetwork(null), ruleOnNetwork(5L))));
    }

    @Test
    public void staticNatKeepsTheIpAllocated() {
        IPAddressVO ip = Mockito.mock(IPAddressVO.class);
        Mockito.when(ip.getVpcId()).thenReturn(1L);
        Mockito.when(ip.isOneToOneNat()).thenReturn(true);
        Assert.assertTrue(manager.isIpAllocatedToVpc(ip));
    }

    @Test
    public void unassignIPFromVpcNetworkUnbindsAnIpWithOnlyVpcFirewallRules() throws ResourceUnavailableException {
        IPAddressVO ip = vpcIpWithRules(ruleOnNetwork(null));
        Mockito.when(ipAddressDao.acquireInLockTable(10L)).thenReturn(ip);
        Network network = Mockito.mock(Network.class);
        Mockito.when(ipAddressManager.applyIpAssociations(network, true)).thenReturn(true);

        manager.unassignIPFromVpcNetwork(ip, network);

        Mockito.verify(ip).setAssociatedWithNetworkId(null);
        Mockito.verify(ipAddressDao).update(10L, ip);
        Mockito.verify(ipAddressDao).releaseFromLockTable(10L);
    }

    @Test
    public void unassignIPFromVpcNetworkKeepsAnIpWithRulesOfANetwork() throws ResourceUnavailableException {
        IPAddressVO ip = vpcIpWithRules(ruleOnNetwork(5L));
        Mockito.when(ipAddressDao.acquireInLockTable(10L)).thenReturn(ip);

        manager.unassignIPFromVpcNetwork(ip, Mockito.mock(Network.class));

        Mockito.verify(ipAddressManager, Mockito.never()).applyIpAssociations(Mockito.any(), Mockito.anyBoolean());
        Mockito.verify(ip, Mockito.never()).setAssociatedWithNetworkId(null);
        Mockito.verify(ipAddressDao).releaseFromLockTable(10L);
    }

    @Test
    public void unassignIPFromVpcNetworkChecksTheIpAsItIsUnderTheLock() throws ResourceUnavailableException {
        IPAddressVO staleIp = vpcIpWithRules();
        IPAddressVO lockedIp = Mockito.mock(IPAddressVO.class);
        Mockito.when(lockedIp.getVpcId()).thenReturn(1L);
        // static NAT was enabled on the IP while the caller was revoking its last rule
        Mockito.when(lockedIp.isOneToOneNat()).thenReturn(true);
        Mockito.when(ipAddressDao.acquireInLockTable(10L)).thenReturn(lockedIp);

        manager.unassignIPFromVpcNetwork(staleIp, Mockito.mock(Network.class));

        Mockito.verify(ipAddressManager, Mockito.never()).applyIpAssociations(Mockito.any(), Mockito.anyBoolean());
        Mockito.verify(ipAddressDao, Mockito.never()).update(Mockito.anyLong(), Mockito.any());
    }

    @Test
    public void unassignIPFromVpcNetworkKeepsAnIpItCannotLock() throws ResourceUnavailableException {
        IPAddressVO ip = vpcIpWithRules();
        Mockito.when(ipAddressDao.acquireInLockTable(10L)).thenReturn(null);

        manager.unassignIPFromVpcNetwork(ip, Mockito.mock(Network.class));

        Mockito.verify(ipAddressManager, Mockito.never()).applyIpAssociations(Mockito.any(), Mockito.anyBoolean());
        Mockito.verify(ipAddressDao, Mockito.never()).update(Mockito.anyLong(), Mockito.any());
    }

    private Account accountCalling(boolean rootAdmin) {
        Account caller = Mockito.mock(Account.class);
        Mockito.when(caller.getId()).thenReturn(7L);
        Mockito.when(accountManager.isRootAdmin(7L)).thenReturn(rootAdmin);
        return caller;
    }

    private void runWithMaxNetworkRate(String maxNetworkRate, Runnable test) throws NoSuchFieldException, IllegalAccessException {
        overrideDefaultConfigValue(VpcManager.VpcMaxNetworkRate, "_defaultValue", maxNetworkRate);
        try {
            test.run();
        } finally {
            overrideDefaultConfigValue(VpcManager.VpcMaxNetworkRate, "_defaultValue", "0");
        }
    }

    @Test
    public void validateVpcNetworkRateAcceptsNoRate() {
        manager.validateVpcNetworkRate(Mockito.mock(Account.class), ACCOUNT_ID, null);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateVpcNetworkRateRefusesANegativeRate() {
        manager.validateVpcNetworkRate(accountCalling(true), ACCOUNT_ID, -2);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void validateVpcNetworkRateRefusesARateTheHypervisorsCannotTake() {
        manager.validateVpcNetworkRate(accountCalling(true), ACCOUNT_ID, VpcManagerImpl.MAX_VPC_NETWORK_RATE_MBPS + 1);
    }

    @Test
    public void validateVpcNetworkRateLetsRootAdminsResetTheRate() {
        manager.validateVpcNetworkRate(accountCalling(true), ACCOUNT_ID, VpcManagerImpl.RESET_VPC_NETWORK_RATE);
    }

    @Test
    public void validateVpcNetworkRateRefusesUsersResettingTheRate() throws Exception {
        runWithMaxNetworkRate("10000", () -> Assert.assertThrows(InvalidParameterValueException.class,
                () -> manager.validateVpcNetworkRate(accountCalling(false), ACCOUNT_ID, VpcManagerImpl.RESET_VPC_NETWORK_RATE)));
    }

    @Test
    public void vpcMaxNetworkRateCanOnlyBeChangedByRootAdmins() {
        // updateConfiguration lets domain admins change account settings of their domain, except system ones
        Assert.assertEquals(ConfigKey.CATEGORY_SYSTEM, VpcManager.VpcMaxNetworkRate.category());
    }

    @Test
    public void validateVpcNetworkRateLetsRootAdminsSetAnyRate() {
        Account caller = accountCalling(true);
        manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 0);
        manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 100000);
    }

    @Test(expected = PermissionDeniedException.class)
    public void validateVpcNetworkRateRefusesUsersWithoutACeiling() {
        manager.validateVpcNetworkRate(accountCalling(false), ACCOUNT_ID, 1000);
    }

    @Test
    public void validateVpcNetworkRateRefusesUsersAboveTheCeilingOrUnlimited() throws Exception {
        runWithMaxNetworkRate("10000", () -> {
            Account caller = accountCalling(false);
            Assert.assertThrows(InvalidParameterValueException.class, () -> manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 10001));
            Assert.assertThrows(InvalidParameterValueException.class, () -> manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 0));
        });
    }

    @Test
    public void validateVpcNetworkRateLetsUsersSetUpToTheCeiling() throws Exception {
        runWithMaxNetworkRate("10000", () -> {
            Account caller = accountCalling(false);
            manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 1);
            manager.validateVpcNetworkRate(caller, ACCOUNT_ID, 10000);
        });
    }

    @Test
    public void getVpcNetworkRateReadsTheVpcDetail() {
        Mockito.when(vpcDetailsDao.findDetail(1L, VpcManager.NETWORK_RATE_DETAIL)).thenReturn(new VpcDetailVO(1L, VpcManager.NETWORK_RATE_DETAIL, "5000", true));
        Assert.assertEquals(Integer.valueOf(5000), manager.getVpcNetworkRate(1L));
    }

    @Test
    public void getVpcNetworkRateIsNullWithoutOrWithAnInvalidDetail() {
        Assert.assertNull(manager.getVpcNetworkRate(1L));
        Mockito.when(vpcDetailsDao.findDetail(2L, VpcManager.NETWORK_RATE_DETAIL)).thenReturn(new VpcDetailVO(2L, VpcManager.NETWORK_RATE_DETAIL, "fast", true));
        Assert.assertNull(manager.getVpcNetworkRate(2L));
    }

    private DomainRouterVO vpcRouter(VirtualMachine.State state) {
        DomainRouterVO router = Mockito.mock(DomainRouterVO.class);
        Mockito.when(router.getId()).thenReturn(50L);
        Mockito.when(router.getState()).thenReturn(state);
        Mockito.when(router.getHypervisorType()).thenReturn(HypervisorType.KVM);
        Mockito.when(router.getHostId()).thenReturn(60L);
        Mockito.when(router.getInstanceName()).thenReturn("r-50-VM");
        Mockito.when(routerDao.listByVpcId(1L)).thenReturn(List.of(router));
        return router;
    }

    private NicVO routerNic(long networkId, TrafficType trafficType, String mac) {
        NicVO nic = Mockito.mock(NicVO.class);
        Mockito.when(nic.getNetworkId()).thenReturn(networkId);
        Mockito.when(nic.getMacAddress()).thenReturn(mac);
        NetworkVO network = Mockito.mock(NetworkVO.class);
        Mockito.when(network.getTrafficType()).thenReturn(trafficType);
        Mockito.when(networkDao.findById(networkId)).thenReturn(network);
        return nic;
    }

    private VpcVO vpcWithId1() {
        VpcVO vpc = Mockito.mock(VpcVO.class);
        Mockito.when(vpc.getId()).thenReturn(1L);
        return vpc;
    }

    @Test
    public void storeVpcNetworkRateStoresTheRateAsAVpcDetail() {
        manager.storeVpcNetworkRate(vpcWithId1(), 2000);

        Mockito.verify(vpcDetailsDao).addDetail(1L, VpcManager.NETWORK_RATE_DETAIL, "2000", true);
    }

    @Test
    public void storeVpcNetworkRateResetRemovesTheVpcDetail() {
        manager.storeVpcNetworkRate(vpcWithId1(), VpcManagerImpl.RESET_VPC_NETWORK_RATE);

        Mockito.verify(vpcDetailsDao).removeDetail(1L, VpcManager.NETWORK_RATE_DETAIL);
        Mockito.verify(vpcDetailsDao, Mockito.never()).addDetail(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
    }

    @Test
    public void applyVpcNetworkRateSetsTheRateOfThePublicNicsOnTheRunningRouters() {
        vpcRouter(VirtualMachine.State.Running);
        NicVO publicNic = routerNic(70L, TrafficType.Public, "1e:00:00:00:00:01");
        NicVO guestNic = routerNic(71L, TrafficType.Guest, "02:00:00:00:00:02");
        Mockito.when(nicDao.listByVmId(50L)).thenReturn(List.of(publicNic, guestNic));
        Mockito.when(networkModel.getNetworkRate(70L, 50L)).thenReturn(2000);
        Mockito.when(agentManager.easySend(Mockito.eq(60L), Mockito.any(UpdateVmNicCommand.class))).thenReturn(new Answer(null, true, null));

        Assert.assertTrue(manager.applyVpcNetworkRate(vpcWithId1()).isEmpty());

        ArgumentCaptor<UpdateVmNicCommand> command = ArgumentCaptor.forClass(UpdateVmNicCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(60L), command.capture());
        Assert.assertEquals("1e:00:00:00:00:01", command.getValue().getNicMacAddress());
        Assert.assertEquals("r-50-VM", command.getValue().getVmName());
        Assert.assertEquals(Integer.valueOf(2000), command.getValue().getNetworkRateMbps());
        Assert.assertNull(command.getValue().isEnabled());
    }

    @Test
    public void applyVpcNetworkRateRemovesTheLimitForAnUnlimitedRate() {
        vpcRouter(VirtualMachine.State.Running);
        NicVO publicNic = routerNic(70L, TrafficType.Public, "1e:00:00:00:00:01");
        Mockito.when(nicDao.listByVmId(50L)).thenReturn(List.of(publicNic));
        Mockito.when(networkModel.getNetworkRate(70L, 50L)).thenReturn(-1);
        Mockito.when(agentManager.easySend(Mockito.eq(60L), Mockito.any(UpdateVmNicCommand.class))).thenReturn(new Answer(null, true, null));

        manager.applyVpcNetworkRate(vpcWithId1());

        ArgumentCaptor<UpdateVmNicCommand> command = ArgumentCaptor.forClass(UpdateVmNicCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(60L), command.capture());
        Assert.assertEquals(Integer.valueOf(0), command.getValue().getNetworkRateMbps());
    }

    @Test
    public void applyVpcNetworkRateLeavesStoppedRoutersForTheirNextStart() {
        vpcRouter(VirtualMachine.State.Stopped);

        Assert.assertTrue(manager.applyVpcNetworkRate(vpcWithId1()).isEmpty());

        Mockito.verify(agentManager, Mockito.never()).easySend(Mockito.anyLong(), Mockito.any());
    }

    @Test
    public void applyVpcNetworkRateReportsARouterThatIsStarting() {
        vpcRouter(VirtualMachine.State.Starting);

        Assert.assertEquals(1, manager.applyVpcNetworkRate(vpcWithId1()).size());

        Mockito.verify(agentManager, Mockito.never()).easySend(Mockito.anyLong(), Mockito.any());
    }

    @Test
    public void applyVpcNetworkRateReportsAHostThatCannotApplyIt() {
        vpcRouter(VirtualMachine.State.Running);
        NicVO publicNic = routerNic(70L, TrafficType.Public, "1e:00:00:00:00:01");
        Mockito.when(nicDao.listByVmId(50L)).thenReturn(List.of(publicNic));
        Mockito.when(networkModel.getNetworkRate(70L, 50L)).thenReturn(2000);
        Mockito.when(agentManager.easySend(Mockito.eq(60L), Mockito.any(UpdateVmNicCommand.class))).thenReturn(new Answer(null, false, "no domain"));

        Assert.assertEquals(1, manager.applyVpcNetworkRate(vpcWithId1()).size());
    }

    private VpcManagerImpl spyUpdatingVpc1(VpcVO vpcVO, List<String> applyProblems) {
        Mockito.when(vpcVO.getAccountId()).thenReturn(ACCOUNT_ID);
        Mockito.when(vpcDao.findById(1L)).thenReturn(vpcVO);
        Mockito.when(vpcDao.createForUpdate(1L)).thenReturn(vpcVO);
        Mockito.when(accountManager.isRootAdmin(Mockito.anyLong())).thenReturn(true);
        VpcManagerImpl spy = Mockito.spy(manager);
        Mockito.doNothing().when(spy).storeVpcNetworkRate(Mockito.any(), Mockito.anyInt());
        Mockito.doReturn(applyProblems).when(spy).applyVpcNetworkRate(Mockito.any());
        return spy;
    }

    @Test
    public void updateVpcAppliesAnUnchangedNetworkRateAgain() throws Exception {
        VpcVO vpcVO = Mockito.mock(VpcVO.class);
        VpcManagerImpl spy = spyUpdatingVpc1(vpcVO, List.of());

        spy.updateVpc(1L, null, null, null, null, null, null, null, 2000);

        Mockito.verify(spy).storeVpcNetworkRate(vpcVO, 2000);
        Mockito.verify(spy).applyVpcNetworkRate(vpcVO);
    }

    @Test
    public void updateVpcSavesTheOtherChangesBeforeTheRateFailsToApply() throws Exception {
        VpcVO vpcVO = Mockito.mock(VpcVO.class);
        VpcManagerImpl spy = spyUpdatingVpc1(vpcVO, List.of("router r-50-VM: no domain"));

        CloudRuntimeException e = Assert.assertThrows(CloudRuntimeException.class,
                () -> spy.updateVpc(1L, "new-name", null, null, null, null, null, null, 2000));

        Assert.assertTrue(e.getMessage().contains("r-50-VM"));
        Mockito.verify(vpcVO).setName("new-name");
        InOrder inOrder = Mockito.inOrder(spy, vpcDao);
        inOrder.verify(spy).storeVpcNetworkRate(vpcVO, 2000);
        inOrder.verify(vpcDao).update(1L, vpcVO);
        inOrder.verify(spy).applyVpcNetworkRate(vpcVO);
    }
}
