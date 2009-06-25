/*
 *  Copyright 2009 Helder Suzuki <heldersuzuki@gmail.com>.
 */
package org.gephi.layout.force;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import org.gephi.graph.api.ClusteredGraph;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.layout.api.Layout;
import org.gephi.layout.force.quadtree.QuadTree;

/**
 *
 * @author Helder Suzuki <heldersuzuki@gmail.com>
 */
public abstract class AbstractForceLayout implements Layout {

    protected ClusteredGraph graph;
    protected float energy0;
    protected float energy;

    public void initAlgo(ClusteredGraph graph) {
        this.graph = graph;

        for (Node n : graph.getTopNodes()) {
            n.getNodeData().setLayoutData(new ForceVector());
        }
        energy = Float.POSITIVE_INFINITY;
    }

    public void initAlgo(GraphController graphController) {
        initAlgo(graphController.getClusteredDirectedGraph());
    }

    /* Maximum level for Barnes-Hut's quadtree */
    protected int getBarnesHutMaxLevel() {
        return Integer.MAX_VALUE;
    }

    /* theta is the parameter for Barnes-Hut opening criteria */
    protected float getBarnesHutTheta() {
        return (float) 1.2;
    }

    private Node getTopmostParent(ClusteredGraph graph, Node n) {
        Node parent = graph.getParent(n);
        while (parent != null) {
            n = parent;
            parent = graph.getParent(n);
        }
        return n;
    }

    private Collection<EdgeImpl> getTopEdges(ClusteredGraph graph) {
        HashSet<EdgeImpl> edges = new HashSet<EdgeImpl>();

        for (Edge e : graph.getEdges()) {
            Node n1 = getTopmostParent(graph, e.getSource());
            Node n2 = getTopmostParent(graph, e.getTarget());
            if (n1 != n2 && graph.getLevel(n1) == 0) {
                edges.add(new EdgeImpl(n1, n2));
            }
        }

        return edges;
    }

    public void goAlgo() {
        // Evaluates n^2 inter node forces using BarnesHut.
        // TODO: NOT WORKING!!!!
        BarnesHut barnes = new BarnesHut(getNodeForce());
        barnes.theta = getBarnesHutTheta();
        QuadTree tree = QuadTree.buildTree(graph,
                                           getBarnesHutMaxLevel());
        for (Node node : graph.getTopNodes()) {
            NodeData data = node.getNodeData();
            ForceVector layoutData = data.getLayoutData();

            if (layoutData == null) {
                System.out.println("layouData == null: " + graph.getLevel(node));
            } else {
                ForceVector f = barnes.calculateForce(data, tree);
                layoutData.add(f);
            }
        }

        // Apply edge forces.
        AbstractForce edgeForce = getEdgeForce();
        int count = 0;
        for (Edge e : getTopEdges(graph)) {
            count++;
            if (graph.getLevel(e.getSource()) == 0 &&
                graph.getLevel(e.getTarget()) == 0) {

                NodeData n1 = e.getSource().getNodeData();
                NodeData n2 = e.getTarget().getNodeData();
                ForceVector f1 = n1.getLayoutData();
                ForceVector f2 = n2.getLayoutData();

                ForceVector f = edgeForce.calculateForce(n1, n2);
                f1.add(f);
                f2.subtract(f);
            }
        }
        System.out.println("count = " + count);

        // Apply displacements on nodes.
        energy0 = energy;
        energy = 0;
        Displacement displacement = getDisplacement();
        for (Node n : graph.getTopNodes()) {
            NodeData data = n.getNodeData();
            ForceVector force = data.getLayoutData();

            energy += force.getEnergy();
            displacement.moveNode(data, force);
        }

        postAlgo();
    }

    protected abstract void postAlgo();

    protected abstract AbstractForce getNodeForce();

    protected abstract AbstractForce getEdgeForce();

    protected abstract boolean hasConverged();

    protected abstract Displacement getDisplacement();

    public boolean canAlgo() {
        return !hasConverged();
    }
}
