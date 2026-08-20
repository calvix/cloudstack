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

package org.apache.cloudstack.backup;

import com.cloud.agent.api.Command;
import com.cloud.agent.api.LogLevel;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TakeBackupCommand extends Command {
    private String vmName;
    private String backupPath;
    private String backupRepoType;
    private String backupRepoAddress;
    private List<PrimaryDataStoreTO> volumePools;
    private List<String> volumePaths;
    private Boolean quiesce;
    @LogLevel(LogLevel.Log4jLevel.Off)
    private String mountOptions;
    /**
     * Passphrase of every encrypted volume of the VM, keyed by volume path (the uuid the backup
     * files are named after). Volumes that are not encrypted are absent. The agent uses these to
     * make the backup itself LUKS-encrypted, so an encrypted volume is never written out in the
     * clear. Keyed rather than positional because the running-VM path enumerates disks from
     * libvirt instead of from {@link #volumePaths}.
     */
    @LogLevel(LogLevel.Log4jLevel.Off)
    private Map<String, byte[]> volumePassphrases;

    // Incremental backup fields (NAS provider; null/empty for legacy full-only callers).
    private String mode;          // "full" or "incremental"; null => legacy behaviour (script default)
    private String bitmapNew;     // Checkpoint/bitmap name to create with this backup (timestamp-based)
    private String bitmapParent;  // Incremental: parent bitmap to read changes since

    // Per-volume parent backup file paths (one per VM volume, ordered by deviceId — same
    // order as volumePaths). The script rebases each new qcow2 onto the matching parent.
    // Backup file UUIDs differ across volumes, so a single parentPath would have rebased
    // every data disk onto the root file. New callers MUST populate parentPaths.
    private List<String> parentPaths;

    public TakeBackupCommand(String vmName, String backupPath) {
        super();
        this.vmName = vmName;
        this.backupPath = backupPath;
    }

    public Map<String, byte[]> getVolumePassphrases() {
        return volumePassphrases;
    }

    public void setVolumePassphrases(Map<String, byte[]> volumePassphrases) {
        this.volumePassphrases = volumePassphrases;
    }

    /** Wipes the passphrases once the command has been handled. */
    public void clearPassphrases() {
        if (volumePassphrases == null) {
            return;
        }
        for (byte[] passphrase : volumePassphrases.values()) {
            if (passphrase != null) {
                Arrays.fill(passphrase, (byte) 0);
            }
        }
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    public String getBackupRepoType() {
        return backupRepoType;
    }

    public void setBackupRepoType(String backupRepoType) {
        this.backupRepoType = backupRepoType;
    }

    public String getBackupRepoAddress() {
        return backupRepoAddress;
    }

    public void setBackupRepoAddress(String backupRepoAddress) {
        this.backupRepoAddress = backupRepoAddress;
    }

    public String getMountOptions() {
        return mountOptions;
    }

    public void setMountOptions(String mountOptions) {
        this.mountOptions = mountOptions;
    }

    public List<PrimaryDataStoreTO> getVolumePools() {
        return volumePools;
    }

    public void setVolumePools(List<PrimaryDataStoreTO> volumePools) {
        this.volumePools = volumePools;
    }

    public List<String> getVolumePaths() {
        return volumePaths;
    }

    public void setVolumePaths(List<String> volumePaths) {
        this.volumePaths = volumePaths;
    }

    public Boolean getQuiesce() {
        return quiesce;
    }

    public void setQuiesce(Boolean quiesce) {
        this.quiesce = quiesce;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getBitmapNew() {
        return bitmapNew;
    }

    public void setBitmapNew(String bitmapNew) {
        this.bitmapNew = bitmapNew;
    }

    public String getBitmapParent() {
        return bitmapParent;
    }

    public void setBitmapParent(String bitmapParent) {
        this.bitmapParent = bitmapParent;
    }

    public List<String> getParentPaths() {
        return parentPaths;
    }

    public void setParentPaths(List<String> parentPaths) {
        this.parentPaths = parentPaths;
    }

    @Override
    public boolean executeInSequence() {
        return true;
    }
}
