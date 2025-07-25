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

package org.apache.hadoop.ozone.recon.api;

import static org.apache.hadoop.hdds.scm.ScmConfigKeys.OZONE_SCM_SERVICE_IDS_KEY;
import static org.apache.hadoop.ozone.om.OMConfigKeys.OZONE_OM_SERVICE_IDS_KEY;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.BUCKET_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.DELETED_DIR_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.DELETED_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.FILE_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.KEY_TABLE;
import static org.apache.hadoop.ozone.om.codec.OMDBDefinition.VOLUME_TABLE;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.scm.container.placement.metrics.SCMNodeStat;
import org.apache.hadoop.hdds.scm.node.NodeStatus;
import org.apache.hadoop.hdds.scm.server.OzoneStorageContainerManager;
import org.apache.hadoop.ozone.recon.api.types.ClusterStateResponse;
import org.apache.hadoop.ozone.recon.api.types.ContainerStateCounts;
import org.apache.hadoop.ozone.recon.api.types.DatanodeStorageReport;
import org.apache.hadoop.ozone.recon.persistence.ContainerHealthSchemaManager;
import org.apache.hadoop.ozone.recon.scm.ReconContainerManager;
import org.apache.hadoop.ozone.recon.scm.ReconNodeManager;
import org.apache.hadoop.ozone.recon.scm.ReconPipelineManager;
import org.apache.hadoop.ozone.recon.tasks.OmTableInsightTask;
import org.apache.ozone.recon.schema.ContainerSchemaDefinition;
import org.apache.ozone.recon.schema.generated.tables.daos.GlobalStatsDao;
import org.apache.ozone.recon.schema.generated.tables.pojos.GlobalStats;
import org.apache.ozone.recon.schema.generated.tables.pojos.UnhealthyContainers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoint to fetch current state of ozone cluster.
 */
@Path("/clusterState")
@Produces(MediaType.APPLICATION_JSON)
public class ClusterStateEndpoint {

  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterStateEndpoint.class);
  public static final int MISSING_CONTAINER_COUNT_LIMIT = 1001;

  private ReconNodeManager nodeManager;
  private ReconPipelineManager pipelineManager;
  private ReconContainerManager containerManager;
  private GlobalStatsDao globalStatsDao;
  private OzoneConfiguration ozoneConfiguration;
  private final ContainerHealthSchemaManager containerHealthSchemaManager;

  @Inject
  ClusterStateEndpoint(OzoneStorageContainerManager reconSCM,
                       GlobalStatsDao globalStatsDao,
                       ContainerHealthSchemaManager
                           containerHealthSchemaManager,
                       OzoneConfiguration ozoneConfiguration) {
    this.nodeManager =
        (ReconNodeManager) reconSCM.getScmNodeManager();
    this.pipelineManager = (ReconPipelineManager) reconSCM.getPipelineManager();
    this.containerManager =
        (ReconContainerManager) reconSCM.getContainerManager();
    this.globalStatsDao = globalStatsDao;
    this.containerHealthSchemaManager = containerHealthSchemaManager;
    this.ozoneConfiguration = ozoneConfiguration;
  }

  /**
   * Return a summary report on current cluster state.
   * @return {@link Response}
   */
  @GET
  public Response getClusterState() {
    ContainerStateCounts containerStateCounts = new ContainerStateCounts();
    int pipelines = this.pipelineManager.getPipelines().size();

    List<UnhealthyContainers> missingContainers = containerHealthSchemaManager
        .getUnhealthyContainers(
            ContainerSchemaDefinition.UnHealthyContainerStates.MISSING,
            0L, Optional.empty(), MISSING_CONTAINER_COUNT_LIMIT);

    containerStateCounts.setMissingContainerCount(
        missingContainers.size() == MISSING_CONTAINER_COUNT_LIMIT ?
            MISSING_CONTAINER_COUNT_LIMIT : missingContainers.size());

    containerStateCounts.setOpenContainersCount(
        this.containerManager.getContainerStateCount(
            HddsProtos.LifeCycleState.OPEN));

    containerStateCounts.setDeletedContainersCount(
        this.containerManager.getContainerStateCount(
            HddsProtos.LifeCycleState.DELETED));

    int healthyDatanodes =
        nodeManager.getNodeCount(NodeStatus.inServiceHealthy()) +
            nodeManager.getNodeCount(NodeStatus.inServiceHealthyReadOnly());

    SCMNodeStat stats = nodeManager.getStats();
    DatanodeStorageReport storageReport =
        new DatanodeStorageReport(stats.getCapacity().get(),
            stats.getScmUsed().get(), stats.getRemaining().get(),
            stats.getCommitted().get());

    ClusterStateResponse.Builder builder = ClusterStateResponse.newBuilder();
    GlobalStats volumeRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(VOLUME_TABLE));
    GlobalStats bucketRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(BUCKET_TABLE));
    // Keys from OBJECT_STORE buckets.
    GlobalStats keyRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(KEY_TABLE));
    // Keys from FILE_SYSTEM_OPTIMIZED buckets
    GlobalStats fileRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(FILE_TABLE));
    // Keys from the DeletedTable
    GlobalStats deletedKeyRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(DELETED_TABLE));
    // Directories from the DeletedDirectoryTable
    GlobalStats deletedDirRecord = globalStatsDao.findById(
        OmTableInsightTask.getTableCountKeyFromTable(DELETED_DIR_TABLE));

    if (volumeRecord != null) {
      builder.setVolumes(volumeRecord.getValue());
    }
    if (bucketRecord != null) {
      builder.setBuckets(bucketRecord.getValue());
    }

    Long totalKeys = 0L;
    Long keysPendingDeletion = 0L;
    Long deletedDirs = 0L;

    if (keyRecord != null) {
      totalKeys += keyRecord.getValue();
    }
    if (fileRecord != null) {
      totalKeys += fileRecord.getValue();
    }
    if (deletedKeyRecord != null) {
      keysPendingDeletion += deletedKeyRecord.getValue();
    }
    if (deletedDirRecord != null) {
      deletedDirs += deletedDirRecord.getValue();
    }

    builder.setKeys(totalKeys);
    builder.setKeysPendingDeletion(keysPendingDeletion);
    builder.setDeletedDirs(deletedDirs);

    // Subtract deleted containers from total containers.
    containerStateCounts.setTotalContainerCount(
        this.containerManager.getContainers().size() -
            containerStateCounts.getDeletedContainersCount());
    ClusterStateResponse response = builder
        .setStorageReport(storageReport)
        .setPipelines(pipelines)
        .setContainers(containerStateCounts.getTotalContainerCount())
        .setMissingContainers(containerStateCounts.getMissingContainerCount())
        .setTotalDatanodes(nodeManager.getAllNodeCount())
        .setHealthyDatanodes(healthyDatanodes)
        .setOpenContainers(containerStateCounts.getOpenContainersCount())
        .setDeletedContainers(containerStateCounts.getDeletedContainersCount())
        .setScmServiceId(ozoneConfiguration.get(OZONE_SCM_SERVICE_IDS_KEY))
        .setOmServiceId(ozoneConfiguration.get(OZONE_OM_SERVICE_IDS_KEY))
        .build();
    return Response.ok(response).build();
  }
}
