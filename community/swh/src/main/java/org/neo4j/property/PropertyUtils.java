package org.neo4j.property;

public class PropertyUtils {
    public enum NodeLabel {
        CNT,
        DIR,
        ORI,
        REL,
        REV,
        SNP;

        NodeLabel() {
        }

        public static PropertyUtils.NodeLabel fromInt(int intType) {
            return switch (intType) {
                case 1 -> CNT;
                case 2 -> DIR;
                case 3 -> ORI;
                case 4 -> REL;
                case 5 -> REV;
                case 6 -> SNP;
                default -> null;
            };
        }

        public static int toInt(PropertyUtils.NodeLabel type) {
            return switch (type) {
                case CNT -> 1;
                case DIR -> 2;
                case ORI -> 3;
                case REL -> 4;
                case REV -> 5;
                case SNP -> 6;
                default -> -1;
            };
        }

        public static PropertyUtils.NodeLabel toNodeLabel(String labelName) {
            return switch (labelName) {
                case "CNT" -> CNT;
                case "DIR" -> DIR;
                case "ORI" -> ORI;
                case "REL" -> REL;
                case "REV" -> REV;
                case "SNP" -> SNP;
                default -> null;
            };
        }
    }

    public enum NodeProperty {
        AUTHOR_TIMESTAMP,
        SWHID,
        LABEL;

        NodeProperty() {
        }

        public static PropertyUtils.NodeProperty fromInt(int intType) {
            return switch (intType) {
                case 1 -> AUTHOR_TIMESTAMP;
                case 2 -> SWHID;
                case 3 -> LABEL;
                default -> null;
            };
        }

        public static int toInt(PropertyUtils.NodeProperty propertyName) {
            return switch (propertyName) {
                case AUTHOR_TIMESTAMP -> 1;
                case SWHID -> 2;
                case LABEL -> 3;
                default -> -1;
            };
        }

        public static PropertyUtils.NodeProperty toNodeProperty(String propertyName) {
            return switch (propertyName) {
                case "author_timestamp" -> AUTHOR_TIMESTAMP;
                case "SWHID" -> SWHID;
                case "label" -> LABEL;
                default -> null;
            };
        }

        public static String toString(PropertyUtils.NodeProperty propertyName) {
            return switch (propertyName) {
                case AUTHOR_TIMESTAMP -> "author_timestamp";
                case SWHID -> "SWHID";
                case LABEL -> "label";
                default -> null;
            };
        }
    }

    public enum RelationshipLabel {
        RELATIONSHIP;

        RelationshipLabel() {
        }

        public static PropertyUtils.RelationshipLabel fromInt(int intType) {
            return switch (intType) {
                case 1 -> RELATIONSHIP;
                default -> null;
            };
        }

        public static int toInt(PropertyUtils.RelationshipLabel type) {
            return switch (type) {
                case RELATIONSHIP -> 1;
                default -> -1;
            };
        }

        public static PropertyUtils.RelationshipLabel toRelationshipLabel(String labelName) {
            return switch (labelName) {
                case "relationship" -> RELATIONSHIP;
                default -> null;
            };
        }
    }

    public enum RelationshipProperty {
        DIR_ENTRY;

        RelationshipProperty() {
        }

        public static PropertyUtils.RelationshipProperty fromInt(int intType) {
            return switch (intType) {
                case 1 -> DIR_ENTRY;
                default -> null;
            };
        }

        public static int toInt(PropertyUtils.RelationshipProperty propertyName) {
            return switch (propertyName) {
                case DIR_ENTRY -> 1;
                default -> -1;
            };
        }

        public static PropertyUtils.RelationshipProperty toRelationshipProperty(String propertyName) {
            return switch (propertyName) {
                case "dir_entry" -> DIR_ENTRY;
                default -> null;
            };
        }

        public static String toString(PropertyUtils.RelationshipProperty propertyName) {
            return switch (propertyName) {
                case DIR_ENTRY -> "dir_entry";
                default -> null;
            };
        }
    }
}
