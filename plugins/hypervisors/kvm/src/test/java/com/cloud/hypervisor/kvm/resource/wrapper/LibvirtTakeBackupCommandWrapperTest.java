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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class LibvirtTakeBackupCommandWrapperTest {

    private final LibvirtTakeBackupCommandWrapper wrapper = new LibvirtTakeBackupCommandWrapper();

    private long parseBackupSize(String stdout, List<String> diskPaths) throws Exception {
        Method method = LibvirtTakeBackupCommandWrapper.class
                .getDeclaredMethod("parseBackupSize", String.class, List.class);
        method.setAccessible(true);
        return (long) method.invoke(wrapper, stdout, diskPaths);
    }

    private static final List<String> TWO_DISKS = Arrays.asList("/disk/a", "/disk/b");

    @Test
    public void testMultiVolumeSumsEveryDisk() throws Exception {
        Assert.assertEquals(3145728L, parseBackupSize("1048576\n2097152", TWO_DISKS));
    }

    @Test
    public void testSingleVolumeTakesTheLastLine() throws Exception {
        Assert.assertEquals(2097152L, parseBackupSize("1048576\n2097152", null));
    }

    /**
     * A mount helper or storage client warning on the same stream must not abort the
     * backup. Before this was tolerated, one such line threw NumberFormatException and
     * left the backup in BackingUp for ever, blocking every later restore of the
     * instance. Only the multi-volume branch was affected, which is the branch taken
     * for a STOPPED instance.
     */
    @Test
    public void testMultiVolumeIgnoresNonNumericLines() throws Exception {
        String stdout = "2026-08-25T17:38:38.403+0000 -1 auth: unable to find a keyring on /etc/ceph/ceph.keyring\n"
                + "1048576\n"
                + "2097152";
        Assert.assertEquals(3145728L, parseBackupSize(stdout, TWO_DISKS));
    }

    @Test
    public void testSingleVolumeSkipsTrailingNoise() throws Exception {
        String stdout = "1048576\nmount: warning: something happened";
        Assert.assertEquals(1048576L, parseBackupSize(stdout, null));
    }

    @Test
    public void testMultiVolumeKeepsTrailingTokensOnASizeLine() throws Exception {
        // Size lines may carry the path after the byte count; only the first token counts.
        Assert.assertEquals(3145728L, parseBackupSize("1048576 /disk/a\n2097152 /disk/b", TWO_DISKS));
    }

    @Test
    public void testBlankLinesAreIgnored() throws Exception {
        Assert.assertEquals(1048576L, parseBackupSize("\n1048576\n\n", TWO_DISKS));
    }

    @Test
    public void testNoNumericLineYieldsZeroRatherThanThrowing() throws Exception {
        Assert.assertEquals(0L, parseBackupSize("only warnings here\nand here", TWO_DISKS));
        Assert.assertEquals(0L, parseBackupSize("only warnings here", null));
    }
}
