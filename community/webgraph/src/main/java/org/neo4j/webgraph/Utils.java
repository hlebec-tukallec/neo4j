package org.neo4j.webgraph;

public class Utils {
    protected static final long NO_ID = -1;

    public enum NodeLabel {
        CNT,
        DIR,
        ORI,
        REL,
        REV,
        SNP,
        NO_LABEL;

        NodeLabel() {
        }

        public static NodeLabel fromInt(int intType) {
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

        public static int toInt(NodeLabel type) {
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

        public static NodeLabel toNodeLabel(String labelName) {
            return switch(labelName) {
                case "CNT" -> CNT;
                case "DIR" -> DIR;
                case "ORI" -> ORI;
                case "REL" -> REL;
                case "REV" -> REV;
                case "SNP" -> SNP;
                default -> NO_LABEL;
            };
        }
    }

    public enum NodeProperty {
        AUTHOR_TIMESTAMP,
        SWHID,
        NO_PROPERTY;


        NodeProperty() {}

        public static NodeProperty fromInt(int intType) {
            return switch (intType) {
                case 1 -> AUTHOR_TIMESTAMP;
                case 2 -> SWHID;
                default -> null;
            };
        }

        public static int toInt(NodeProperty propertyName) {
            return switch (propertyName) {
                case AUTHOR_TIMESTAMP -> 1;
                case SWHID -> 2;
                case NO_PROPERTY -> -1;
            };
        }

        public static NodeProperty toNodeProperty(String propertyName) {
            return switch (propertyName) {
                case "author_timestamp" -> AUTHOR_TIMESTAMP;
                case  "SWHID" -> SWHID;
                default -> NO_PROPERTY;
            };
        }
    }
}
