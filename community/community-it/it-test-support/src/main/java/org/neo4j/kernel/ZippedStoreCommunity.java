/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel;

import static org.neo4j.common.Edition.COMMUNITY;

public enum ZippedStoreCommunity implements ZippedStore {
    // Stores with special node label index
    AF430_V42_INJECTED_NLI(
            "AF4.3.0_V4.2_empty_community_injected_nli.zip",
            new DbStatistics("AF4.3.0", KernelVersion.V4_2, 1, COMMUNITY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    AF430_V43D4_PERSISTED_NLI(
            "AF4.3.0_V4.3.D4_empty_community_persisted_nli.zip",
            new DbStatistics("AF4.3.0", KernelVersion.V4_3_D4, 2, COMMUNITY, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1)),
    // 4.3 stores
    SF430_V43D4_ALL_NO_BTREE(
            "SF4.3.0_V4.3.D4_all-no-btree_community.zip",
            new DbStatistics(
                    "SF4.3.0",
                    KernelVersion.V4_3_D4,
                    11,
                    COMMUNITY,
                    2587,
                    2576,
                    11,
                    2586,
                    351,
                    3726,
                    3725,
                    11946,
                    10,
                    2,
                    0,
                    0,
                    0,
                    10)),
    AF430_V43D4_ALL_NO_BTREE(
            "AF4.3.0_V4.3.D4_all-no-btree_community.zip",
            new DbStatistics(
                    "AF4.3.0",
                    KernelVersion.V4_3_D4,
                    11,
                    COMMUNITY,
                    2586,
                    2574,
                    12,
                    2585,
                    349,
                    3740,
                    3739,
                    11941,
                    10,
                    2,
                    0,
                    0,
                    0,
                    10)),
    // 4.4 stores
    SF430_V44_EMPTY(
            "SF4.3.0_V4.4_empty_community.zip",
            new DbStatistics("SF4.3.0", KernelVersion.V4_4, 4, COMMUNITY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    AF430_V44_EMPTY(
            "AF4.3.0_V4.4_empty_community.zip",
            new DbStatistics("AF4.3.0", KernelVersion.V4_4, 4, COMMUNITY, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    SF430_V44_ALL(
            "SF4.3.0_V4.4_all_community.zip",
            new DbStatistics(
                    "SF4.3.0",
                    KernelVersion.V4_4,
                    23,
                    COMMUNITY,
                    2585,
                    2573,
                    12,
                    2584,
                    366,
                    3738,
                    3737,
                    11872,
                    36,
                    2,
                    12,
                    8,
                    4,
                    36)),
    AF430_V44_ALL(
            "AF4.3.0_V4.4_all_community.zip",
            new DbStatistics(
                    "AF4.3.0",
                    KernelVersion.V4_4,
                    23,
                    COMMUNITY,
                    2549,
                    2537,
                    12,
                    2548,
                    337,
                    3636,
                    3635,
                    11676,
                    36,
                    2,
                    12,
                    8,
                    4,
                    36));

    private final String zipFileName;
    private final DbStatistics statistics;

    ZippedStoreCommunity(String zipFileName, DbStatistics statistics) {
        this.zipFileName = zipFileName;
        this.statistics = statistics;
    }

    @Override
    public DbStatistics statistics() {
        return statistics;
    }

    @Override
    public String zipFileName() {
        return zipFileName;
    }
}
