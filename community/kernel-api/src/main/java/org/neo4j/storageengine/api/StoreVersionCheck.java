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
package org.neo4j.storageengine.api;

import org.neo4j.io.pagecache.context.CursorContext;

public interface StoreVersionCheck {

    boolean isCurrentStoreVersionFullySupported(CursorContext cursorContext);

    /**
     * Figures out the migration target format version and checks if migration to that version is possible.
     * <p>
     * The migration target version will be the latest version (both latest major and minor) for the submitted format
     * if the submitted format is not {@code null}.
     * If the submitted format is {@code null}, it will be the latest version (both latest major and minor)
     * for the store format the store is currently on.
     */
    MigrationCheckResult getAndCheckMigrationTargetVersion(String formatToMigrateTo, CursorContext cursorContext);

    /**
     * Figures out the upgrade target format version and checks if upgrade to that version is possible.
     * <p>
     * The upgrade target format version is the latest minor format version for the combination of the format
     * and the major version of the store format the store is currently on.
     */
    UpgradeCheckResult getAndCheckUpgradeTargetVersion(CursorContext cursorContext);

    String getIntroductionVersionFromVersion(StoreVersionIdentifier versionIdentifier);

    /**
     * Determine the latest version identifier for the provided storage engine format
     * @param format the format to check and fins the latest version for
     * @return the identifier for the latest version
     */
    StoreVersionIdentifier findLatestVersion(String format);

    record MigrationCheckResult(
            MigrationOutcome outcome,
            StoreVersionIdentifier versionToMigrateFrom,
            StoreVersionIdentifier versionToMigrateTo,
            Exception cause) {}

    enum MigrationOutcome {
        // successful outcomes:
        /**
         * The target migration version is the same as the version the store is currently on.
         */
        NO_OP,
        /**
         * The target migration version has been determined and the migration is possible.
         */
        MIGRATION_POSSIBLE,

        // failure outcomes:
        /**
         * The version the store is currently could not be read or understood.
         * <p>
         * Logically, {@link MigrationCheckResult#versionToMigrateFrom()} and {@link MigrationCheckResult#versionToMigrateTo()}
         * are {@code null} for this outcome.
         */
        STORE_VERSION_RETRIEVAL_FAILURE,
        /**
         * The target migration version has been determined,
         * but the migration path from the version the store is currently on to the determined target migration version is not supported.
         */
        UNSUPPORTED_MIGRATION_PATH,
        /**
         * The target migration version has been determined, but it is no longer supported.
         * Since the migration target version is determined as the latest major and minor version combination for the target format,
         * it means that the target format is no longer supported by the current binaries as there is no supported version for the format.
         * The only possible step is migration to another format.
         */
        UNSUPPORTED_TARGET_VERSION,
    }

    record UpgradeCheckResult(
            UpgradeOutcome outcome,
            StoreVersionIdentifier versionToUpgradeFrom,
            StoreVersionIdentifier versionToUpgradeTo,
            Exception cause) {}

    enum UpgradeOutcome {
        // successful outcomes:

        /**
         * The target upgrade version is the same as the version the store is currently on.
         */
        NO_OP,
        /**
         * The target upgrade version has been determined and the migration is possible.
         */
        UPGRADE_POSSIBLE,

        // failure outcomes:
        /**
         * The version the store is currently could not be read or understod.
         * <p>
         * Logically, {@link UpgradeCheckResult#versionToUpgradeFrom()} and {@link UpgradeCheckResult#versionToUpgradeTo()} are {@code null} for this outcome.
         */
        STORE_VERSION_RETRIEVAL_FAILURE,
        /**
         * The target upgrade version has been determined, but it is no longer supported.
         * Since the upgrade target version is determined as the latest minor version of the format the store is currently on,
         * it means that the current major version of the store format is no longer supported by the current binaries.
         * The only possible step is migration to another major version of the current format or to another format.
         */
        UNSUPPORTED_TARGET_VERSION
    }
}
