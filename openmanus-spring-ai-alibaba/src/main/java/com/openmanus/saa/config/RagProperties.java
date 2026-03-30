package com.openmanus.saa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.rag")
public class RagProperties {

    private boolean enabled = false;
    private String backend = "elasticsearch";
    private int defaultTopK = 5;
    private boolean defaultHybrid = true;
    private boolean citationEnabled = true;
    private Retrieval retrieval = new Retrieval();
    private Ingestion ingestion = new Ingestion();
    private Elasticsearch elasticsearch = new Elasticsearch();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public boolean isDefaultHybrid() {
        return defaultHybrid;
    }

    public void setDefaultHybrid(boolean defaultHybrid) {
        this.defaultHybrid = defaultHybrid;
    }

    public boolean isCitationEnabled() {
        return citationEnabled;
    }

    public void setCitationEnabled(boolean citationEnabled) {
        this.citationEnabled = citationEnabled;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval == null ? new Retrieval() : retrieval;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(Ingestion ingestion) {
        this.ingestion = ingestion == null ? new Ingestion() : ingestion;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(Elasticsearch elasticsearch) {
        this.elasticsearch = elasticsearch == null ? new Elasticsearch() : elasticsearch;
    }

    public static class Retrieval {
        private double minScore = 0.0d;
        private int maxContextChars = 6000;

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }
    }

    public static class Ingestion {
        private int chunkSize = 800;
        private int chunkOverlap = 120;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static class Elasticsearch {
        private String[] uris = new String[] {"http://localhost:9200"};
        private String indexPrefix = "openmanus_kb";
        private String username = "";
        private String password = "";
        private int vectorDimensions = 1024;

        public String[] getUris() {
            return uris;
        }

        public void setUris(String[] uris) {
            this.uris = uris == null ? new String[] {"http://localhost:9200"} : uris;
        }

        public String getIndexPrefix() {
            return indexPrefix;
        }

        public void setIndexPrefix(String indexPrefix) {
            this.indexPrefix = indexPrefix;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getVectorDimensions() {
            return vectorDimensions;
        }

        public void setVectorDimensions(int vectorDimensions) {
            this.vectorDimensions = vectorDimensions;
        }
    }
}
