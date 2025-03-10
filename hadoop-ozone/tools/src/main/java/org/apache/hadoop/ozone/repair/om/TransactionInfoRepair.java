/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache
 * License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and
 * limitations under the License.
 */
package org.apache.hadoop.ozone.repair.om;

import org.apache.hadoop.hdds.cli.HddsVersionProvider;
import org.apache.hadoop.hdds.utils.IOUtils;
import org.apache.hadoop.hdds.utils.TransactionInfo;
import org.apache.hadoop.hdds.utils.db.StringCodec;
import org.apache.hadoop.hdds.utils.db.managed.ManagedRocksDB;
import org.apache.hadoop.ozone.debug.RocksDBUtils;
import org.apache.hadoop.ozone.repair.RepairTool;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.hadoop.ozone.OzoneConsts.TRANSACTION_INFO_KEY;
import static org.apache.hadoop.ozone.om.OmMetadataManagerImpl.TRANSACTION_INFO_TABLE;

/**
 * Tool to update the highest term-index in transactionInfoTable.
 */
@CommandLine.Command(
    name = "update-transaction",
    description = "CLI to update the highest index in transactionInfoTable. Currently it is only supported for OM.",
    mixinStandardHelpOptions = true,
    versionProvider = HddsVersionProvider.class
)
public class TransactionInfoRepair extends RepairTool {

  @CommandLine.Option(names = {"--db"},
      required = true,
      description = "Database File Path")
  private String dbPath;

  @CommandLine.Option(names = {"--term"},
      required = true,
      description = "Highest term of transactionInfoTable. The input should be non-zero long integer.")
  private long highestTransactionTerm;

  @CommandLine.Option(names = {"--index"},
      required = true,
      description = "Highest index of transactionInfoTable. The input should be non-zero long integer.")
  private long highestTransactionIndex;

  @Override
  public void execute() throws Exception {
    List<ColumnFamilyHandle> cfHandleList = new ArrayList<>();
    List<ColumnFamilyDescriptor> cfDescList = RocksDBUtils.getColumnFamilyDescriptors(
        dbPath);

    try (ManagedRocksDB db = ManagedRocksDB.open(dbPath, cfDescList, cfHandleList)) {
      ColumnFamilyHandle transactionInfoCfh = RocksDBUtils.getColumnFamilyHandle(TRANSACTION_INFO_TABLE, cfHandleList);
      if (transactionInfoCfh == null) {
        throw new IllegalArgumentException(TRANSACTION_INFO_TABLE +
            " is not in a column family in DB for the given path.");
      }
      TransactionInfo originalTransactionInfo =
          RocksDBUtils.getValue(db, transactionInfoCfh, TRANSACTION_INFO_KEY, TransactionInfo.getCodec());

      info("The original highest transaction Info was %s", originalTransactionInfo.getTermIndex());

      TransactionInfo transactionInfo = TransactionInfo.valueOf(highestTransactionTerm, highestTransactionIndex);

      byte[] transactionInfoBytes = TransactionInfo.getCodec().toPersistedFormat(transactionInfo);
      db.get()
          .put(transactionInfoCfh, StringCodec.get().toPersistedFormat(TRANSACTION_INFO_KEY), transactionInfoBytes);

      info("The highest transaction info has been updated to: %s",
          RocksDBUtils.getValue(db, transactionInfoCfh, TRANSACTION_INFO_KEY,
              TransactionInfo.getCodec()).getTermIndex());
    } catch (RocksDBException exception) {
      error("Failed to update the RocksDB for the given path: %s", dbPath);
      error(
          "Make sure that Ozone entity (OM) is not running for the give database path and current host.");
      throw new IOException("Failed to update RocksDB.", exception);
    } finally {
      IOUtils.closeQuietly(cfHandleList);
    }
  }
}
