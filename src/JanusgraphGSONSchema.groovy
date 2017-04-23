import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.shaded.jackson.databind.node.ObjectNode;
import org.apache.tinkerpop.shaded.jackson.databind.node.TextNode
import org.janusgraph.core.Cardinality;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.schema.JanusGraphManagement.IndexBuilder;

/**
 * A utility class to read GraphSON schema document and write to JanusGraph
 */
class JanusgraphGSONSchema {
    private JanusGraph graph;
    private File gsonFile;

    /**
     * Constructor of JansugraphGSONSchema object with the {@code graph}
     * @param graph a JanusGraph and write GraphSON schema into it
     */
    public JanusgraphGSONSchema(JanusGraph graph) {
        if (!graph) {
            throw new Exception("JanusGraph is null");
        }
        this.graph = graph;
    }

    /**
     * Read the GraphSON schema document from {@code gsonSchemaFile}
     * and write to the JanusGraph
     * @param gsonSchemaFile GraphSON schema document and using
     *        IBM Graph GraphSON schema format.
     */
    public void readFile(String gsonSchemaFile) {
        gsonFile = new File(gsonSchemaFile);
        ObjectNode root = null;

        if (!gsonFile.exists()) {
            throw new Exception("file not found:" + gsonSchemaFile);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            root = mapper.readTree(gsonFile);
        } catch (Exception e) {
            throw new Exception("can't parse GSON file:" + e.getMessage());
        }

        JanusGraphManagement mgmt = graph.openManagement();

        for (node in root.get("propertyKeys").asList()) {
            String name = node.get("name").asText();
            if (mgmt.containsPropertyKey(name)) {
                println "property: ${name} exists";
            } else {
                mgmt.makePropertyKey(node.get("name").asText())
                        .dataType(Class.forName("java.lang." + node.get("dataType").asText()))
                        .cardinality(Cardinality.valueOf(node.get("cardinality").asText())).make();
            }
        }

        for (vertex in root.get("vertexLabels").asList()) {
            String name = vertex.get("name").asText();
            if (mgmt.containsVertexLabel(name)) {
                println "vertex: ${name} exists";
            } else {
                mgmt.makeVertexLabel(name).make();
            }
        }

        for (edge in root.get("edgeLabels").asList()) {
            String name = edge.get("name").asText();
            if (mgmt.containsEdgeLabel(name)) {
                println "edge: ${name} exists";
            } else {
                mgmt.makeEdgeLabel(name).make();
            }
        }

        for (vindex in root.get("vertexIndexes").asList()) {
            makeIndex(mgmt, vindex, true);
        }

        for (eindex in root.get("edgeIndexes").asList()) {
            makeIndex(mgmt, eindex, false);
        }

        mgmt.commit();
    }

    private void make(List<ObjectNode> nodes, String name, Closure check, Closure exist, Closure create) {
        for (node in nodes) {
            String nameStr = node.get(name).asText();
            if (check.call(nameStr)) {
                exist.call(nameStr);
            } else {
                create.call(nameStr, node);
            }
        }
    }

    /**
     * Create vertex/edge index based on the {@code node}. If {@code isVertexIndex} equeals true
     * a vertex index is created. Otherwise, create edge index
     *
     * @param mgmt is used to call the {@code buildIndex()}
     * @param node contains "name", "propertyKeys", "unique", "composite" and "mixedIndex" properties
     * @param isVertexIndex create a vertex index or edge index
     */
    private void makeIndex(JanusGraphManagement mgmt, ObjectNode node, boolean isVertexIndex) {
        String name = node.get("name").asText();
        if (mgmt.containsGraphIndex(name)) {
            println "index: ${name} exists";
            return;
        }

        IndexBuilder ib = mgmt.buildIndex(node.get("name").asText(), isVertexIndex ? Vertex.class : Edge.class);
        List<TextNode> properties = node.get("propertyKeys").asList();
        for (property in properties) {
            ib.addKey(mgmt.getPropertyKey(property.asText()));
        }

        if (node.has("unique") && node.get("unique").asBoolean()) {
            ib.unique();
        }

        if (node.has("composite") && node.get("composite").asBoolean()) {
            ib.buildCompositeIndex();
        }

        if (node.has("mixedIndex")) {
            ib.buildMixedIndex(node.get("mixedIndex").asText());
        }
    }
}