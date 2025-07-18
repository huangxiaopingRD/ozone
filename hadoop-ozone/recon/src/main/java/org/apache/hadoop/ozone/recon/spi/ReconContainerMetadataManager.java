/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.recon.spi;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.apache.hadoop.hdds.annotation.InterfaceStability;
import org.apache.hadoop.hdds.utils.db.BatchOperation;
import org.apache.hadoop.hdds.utils.db.RDBBatchOperation;
import org.apache.hadoop.hdds.utils.db.Table;
import org.apache.hadoop.hdds.utils.db.TableIterator;
import org.apache.hadoop.ozone.recon.api.types.ContainerKeyPrefix;
import org.apache.hadoop.ozone.recon.api.types.ContainerMetadata;
import org.apache.hadoop.ozone.recon.api.types.KeyPrefixContainer;
import org.apache.hadoop.ozone.recon.scm.ContainerReplicaHistory;
import org.apache.hadoop.ozone.util.SeekableIterator;

/**
 * The Recon Container DB Service interface.
 */
@InterfaceStability.Unstable
public interface ReconContainerMetadataManager {

  /**
   * Create new container DB and bulk Store the container to Key prefix
   * mapping.
   * @param containerKeyPrefixCounts Map of containerId, key-prefix tuple to
   *                                 key count.
   */
  void reinitWithNewContainerDataFromOm(Map<ContainerKeyPrefix, Integer>
                                    containerKeyPrefixCounts)
      throws IOException;

  /**
   * Store the container to Key prefix mapping into the Recon Container DB.
   *
   * @param containerKeyPrefix the containerId, key-prefix tuple.
   * @param count              Count of Keys with that prefix.
   */
  @Deprecated
  void storeContainerKeyMapping(ContainerKeyPrefix containerKeyPrefix,
                                Integer count) throws IOException;

  /**
   * Store the container to Key prefix mapping into a batch.
   *
   * @param batch the batch operation we store into
   * @param containerKeyPrefix the containerId, key-prefix tuple.
   * @param count              Count of Keys with that prefix.
   */
  void batchStoreContainerKeyMapping(BatchOperation batch,
                                     ContainerKeyPrefix containerKeyPrefix,
                                     Integer count) throws IOException;

  /**
   * Store the containerID -&gt; no. of keys count into the container DB store.
   *
   * @param containerID the containerID.
   * @param count count of the keys within the given containerID.
   * @throws IOException
   */
  @Deprecated
  void storeContainerKeyCount(Long containerID, Long count) throws IOException;

  /**
   * Store the containerID -&gt; no. of keys count into a batch.
   *
   * @param batch the batch operation we store into
   * @param containerID the containerID.
   * @param count count of the keys within the given containerID.
   * @throws IOException
   */
  void batchStoreContainerKeyCounts(BatchOperation batch, Long containerID,
                                    Long count) throws IOException;

  /**
   * Store the containerID -&gt; ContainerReplicaWithTimestamp mapping to the
   * container DB store.
   *
   * @param containerID the containerID.
   * @param tsMap A map from datanode UUID to ContainerReplicaWithTimestamp.
   * @throws IOException
   */
  void storeContainerReplicaHistory(Long containerID,
      Map<UUID, ContainerReplicaHistory> tsMap) throws IOException;

  /**
   * Batch version of storeContainerReplicaHistory.
   *
   * @param replicaHistoryMap Replica history map
   * @throws IOException
   */
  void batchStoreContainerReplicaHistory(
      Map<Long, Map<UUID, ContainerReplicaHistory>> replicaHistoryMap)
      throws IOException;

  /**
   * Store the total count of containers into the container DB store.
   *
   * @param count count of the containers present in the system.
   */
  void storeContainerCount(Long count);

  /**
   * Get the stored key prefix count for the given containerID, key prefix.
   *
   * @param containerKeyPrefix the containerID, key-prefix tuple.
   * @return count of keys with that prefix.
   */
  Integer getCountForContainerKeyPrefix(
      ContainerKeyPrefix containerKeyPrefix) throws IOException;

  /**
   * Get the total count of keys within the given containerID.
   *
   * @param containerID the given containerId.
   * @return count of keys within the given containerID.
   * @throws IOException
   */
  long getKeyCountForContainer(Long containerID) throws IOException;

  /**
   * Get the container replica history of the given containerID.
   *
   * @param containerID the given containerId.
   * @return A map of ContainerReplicaWithTimestamp of the given containerID.
   * @throws IOException
   */
  Map<UUID, ContainerReplicaHistory> getContainerReplicaHistory(
      Long containerID) throws IOException;

  /**
   * Get if a containerID exists or not.
   *
   * @param containerID the given containerID.
   * @return if the given ContainerID exists or not.
   * @throws IOException
   */
  boolean doesContainerExists(Long containerID) throws IOException;

  /**
   * Get the stored key prefixes for the given containerId.
   *
   * @param containerId the given containerId.
   * @return Map of Key prefix -&gt; count.
   */
  Map<ContainerKeyPrefix, Integer> getKeyPrefixesForContainer(
      long containerId) throws IOException;

  /**
   * Get the stored key prefixes for the given containerId starting
   * after the given keyPrefix.
   *
   * @param containerId the given containerId.
   * @param prevKeyPrefix the key prefix to seek to and start scanning.
   * @return Map of Key prefix -&gt; count.
   */
  Map<ContainerKeyPrefix, Integer> getKeyPrefixesForContainer(
      long containerId, String prevKeyPrefix, int limit) throws IOException;

  /**
   * Get a Map of containerID, containerMetadata of Containers only for the
   * given limit. If the limit is -1 or any integer &lt; 0, then return all
   * the containers without any limit.
   *
   * @param limit the no. of containers to fetch.
   * @param prevContainer containerID after which the results are returned.
   * @return Map of containerID -&gt; containerMetadata.
   * @throws IOException
   */
  Map<Long, ContainerMetadata> getContainers(int limit, long prevContainer)
      throws IOException;

  SeekableIterator<Long, ContainerMetadata> getContainersIterator() throws IOException;

  /**
   * Delete an entry in the container DB.
   *
   * @param containerKeyPrefix container key prefix to be deleted.
   * @throws IOException exception.
   */
  @Deprecated
  void deleteContainerMapping(ContainerKeyPrefix containerKeyPrefix)
      throws IOException;

  /**
   * Add the deletion of an entry to a batch.
   *
   * @param batch the batch operation we add the deletion
   * @param containerKeyPrefix container key prefix to be deleted.
   * @throws IOException exception.
   */
  void batchDeleteContainerMapping(BatchOperation batch,
                                   ContainerKeyPrefix containerKeyPrefix)
      throws IOException;

  /**
   * Get the total count of containers present in the system.
   *
   * @return total count of containers.
   * @throws IOException
   */
  long getCountForContainers() throws IOException;

  /**
   * Increment the total count for containers in the system by the given count.
   *
   * @param count no. of new containers to add to containers total count.
   */
  void incrementContainerCountBy(long count);

  /**
   * Commit a batch operation into the containerDbStore.
   *
   * @param rdbBatchOperation batch operation we want to commit
   */
  void commitBatchOperation(RDBBatchOperation rdbBatchOperation)
      throws IOException;
      
  /**
   * Get iterator to the entire Key_Container DB.
   * @return TableIterator
   */
  TableIterator getKeyContainerTableIterator() throws IOException;

  /**
   * Get the entire keyContainerTable.
   * @return keyContainerTable
   */
  Table<KeyPrefixContainer, Integer> getKeyContainerTable();

  /**
   * Get the stored key prefixes for the given containerId starting
   * after the given keyPrefix.
   *
   * @param prevKeyPrefix the key prefix to seek to and start scanning.
   * @param keyVersion the key version to seek
   * @return Map of Key prefix -&gt; count.
   */
  Map<KeyPrefixContainer, Integer> getContainerForKeyPrefixes(
      String prevKeyPrefix, long keyVersion) throws IOException;
}
