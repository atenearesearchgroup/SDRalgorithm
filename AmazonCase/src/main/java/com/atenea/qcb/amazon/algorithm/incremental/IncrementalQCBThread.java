package com.atenea.qcb.amazon.algorithm.incremental;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.atenea.qcb.amazon.algorithm.IncrementalQCB;
import com.atenea.qcb.amazon.algorithm.QCBAlgorithm;

public class IncrementalQCBThread implements Runnable {
	private final static Logger LOGGER = Logger.getLogger(IncrementalQCBThread.class.getName());

	// Incremental has an instance of Main to read its property myData and to call
	// callBack()
	MainThread main;
	HashSet<String> ids = new HashSet<String>();

	public IncrementalQCBThread(MainThread main) {
		this.main = main;
	}

	@SuppressWarnings({ "rawtypes", "unlikely-arg-type" })
	@Override
	public void run() {

		FileHandler fh = null;
		Boolean incremental = null;
		try {

			while (incremental == null) {
				incremental = main.getIncremental();
			}

			// handler
			while (fh == null) {
				fh = main.getFh();
			}
			LOGGER.addHandler(fh);

			// graph and query
			Graph graph = null;
			GraphTraversal queryTraversal = null;
			Graph subgraph = null;
			while (graph == null) {
				graph = main.getGraph();
			}
			while (queryTraversal == null) {

				queryTraversal = main.getQueryTraversal();
			}

			if (incremental) {
				// precomputation
				Long initP = System.currentTimeMillis();
				subgraph = (Graph) graph.traversal().E().subgraph("g").cap("g").next();
				ids = precomputation(subgraph, queryTraversal);
				main.setSubgraph(subgraph);
				Long endP = System.currentTimeMillis();
				LOGGER.info(endP - initP + " milliseconds for precomputation");
			} else {
				subgraph = (Graph) graph.traversal().E().subgraph("g").cap("g").next();
				main.setSubgraph(subgraph);
			}

			while (true) {
				if (incremental) {
					// First i read the data from MainThread
					Set<Vertex> vertices = main.accessVertices();

					if (!vertices.isEmpty()) {
						graph = main.getGraph();
						subgraph = (Graph) graph.traversal().E().subgraph("g").cap("g").next();
						ids.addAll(IncrementalQCB.updateWeight(vertices, subgraph, queryTraversal)
								.parallelStream().map(n -> String.valueOf(n.get(T.id))).collect(Collectors.toSet()));
						subgraph.traversal().V().hasId(P.without(ids)).drop().iterate();
						main.clearVertices(vertices);
						main.setSubgraph(subgraph);

					}
				} else {
					graph = main.getGraph();
					subgraph = (Graph) graph.traversal().E().subgraph("g").cap("g").next();
					main.setSubgraph(subgraph);
				}

			}

		} catch (SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	@SuppressWarnings({ "rawtypes", "unchecked", "unlikely-arg-type" })
	public HashSet<String> precomputation(Graph graph, GraphTraversal queryTraversal) {

		// Calculate weights
		GraphTraversal graphWeights = graph.traversal().withComputer().V()
				.program(QCBAlgorithm.build().query(queryTraversal.asAdmin()).property("weight").create(null))
				.has("weight", P.gt(0.0d));
		List<Map<String, Object>> ids1 = (List<Map<String, Object>>) graphWeights.valueMap(true, "weight").toList();
		HashSet<String> ids = (HashSet<String>) ids1.parallelStream().map(n -> String.valueOf(n.get(T.id)))
				.collect(Collectors.toSet());
		graph.traversal().V().hasId(P.without(ids)).drop().iterate();

		return ids;
	}
}