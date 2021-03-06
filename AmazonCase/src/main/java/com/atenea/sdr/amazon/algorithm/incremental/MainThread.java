package com.atenea.sdr.amazon.algorithm.incremental;

import static org.apache.tinkerpop.gremlin.structure.Column.values;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
@SuppressWarnings("rawtypes")
public class MainThread implements Runnable {

	private final static Logger LOGGER = Logger.getLogger(MainThread.class.getName());
	// Parameters
	private Set<Vertex> vertices = new HashSet<Vertex>();
	private Graph graph = null;
	private Graph subgraph = null;
	private GraphTraversal queryTraversal = null;
	private Boolean incremental = null;

	Long records = 0L;
	FileHandler fh;

	public MainThread(FileHandler fh) {
		this.fh  = fh;
	}

	@SuppressWarnings("unused")
	public void startComputing() throws IOException, InterruptedException {

		try (InputStream input = new FileInputStream("src/main/resources/config.properties")) {

			Properties prop = new Properties();

			// load a properties file
			prop.load(input);

			// get the property value and print it out
			String fileName = prop.getProperty("fileName");
			Long records = Long.parseLong(prop.getProperty("records"));
			Long recordsQuery = Long.parseLong(prop.getProperty("recordsQuery"));
			String fileWeight = prop.getProperty("nameWeights");
			String query = prop.getProperty("query");
			setIncremental(Boolean.parseBoolean(prop.getProperty("incremental")));

			setGraph(fileName);
			setRecords(records);
			setQueryTraversal(query);

			// handler
			LOGGER.addHandler(fh);

			// File with new orders
			File fileOrders = new File("filesWeight/NewOrders100");
			BufferedReader brOrders = new BufferedReader(new FileReader(fileOrders));
			String stOrders;
			String idOrder = "";

			while (getSubgraph() == null) {
			}

			LOGGER.info(subgraph.toString());

			runQuery(subgraph, query);

			for (int x = 0; x < records; x++) {

				Set<Vertex> newVertices = new HashSet<Vertex>();
				stOrders = brOrders.readLine();

				String[] line = stOrders.split("\t");

				Vertex customer = graph.traversal().V(line[0]).next();
				Vertex order;
				if (graph.traversal().V().has("idOrder", line[1]).hasNext() == false) {
					order = graph.traversal().addV("Order").property("date", (new Date()).getTime())
							.property("idOrder", line[1]).property("address", line[2]).as("order").next();
					newVertices.add(order);
					modifyVertices(newVertices);

					customer.addEdge("orders", order);
				}

				order = graph.traversal().V().has("idOrder", line[1]).next();

				Vertex product = graph.traversal().V().has("idProduct", line[3]).next();

				order.addEdge("contains", product);

				if ((x + 1) % recordsQuery == 0) {

					System.out.println(subgraph);
					runQuery(subgraph, query);

				}
			}
			
			brOrders.close();
		}
	}

	public void runQuery(Graph graphRun, String query) {
		List result = null;
		List<String> idProducts = new ArrayList<String>();
		if (query.equals("3")) {
			for (int i = 1; i <= 30; i++) {
				idProducts.add("product " + i);
			}
		}
		Long start = System.currentTimeMillis(); // init time
		switch (query) {
		case "1":
			result = graphRun.traversal().V().as("user").out("orders").out("contains")
					.has("idProduct", "product 10").select("user").dedup().toList();
			break;
		case "2":
			result = graphRun.traversal().V().where(__.out("orders").out("contains").has("idProduct", "product 10"))
					.dedup().toList();
			break;
		case "3":

			result = graphRun.traversal().V().hasLabel("Customer").as("user")
					.not(__.out("orders").out("contains").has("idProduct", P.within(idProducts))).select("user").dedup()
					.toList();
			break;
		case "4":
			result = graphRun.traversal().V().and(__.out("orders").out("contains").has("idProduct", "product 10"),
					__.out("orders").out("contains").has("idProduct", "product 20")).dedup().toList();
			break;
		case "5":
			result = graphRun.traversal().V().or(__.out("orders").out("contains").has("idProduct", "product 10"),
					__.out("orders").out("contains").has("idProduct", "product 20")).dedup().toList();
			break;
		case "6":
			result = graphRun.traversal().V().has("idProduct", "product 10").in("contains").in("orders").groupCount()
					.unfold().where(__.select(values).is(P.gte(3))).toList();
			break;
		case "7":
			result = graphRun.traversal().V().has("idProduct", P.within("product 10", "product 20")).in("contains")
					.in("orders").groupCount().unfold().where(__.select(values).is(P.gte(3))).toList();
			break;
		}

		Long end = System.currentTimeMillis(); // end time

		LOGGER.info(result.size() + " results for subgraph in " + (end - start) + " milliseconds. Timestamp end: " + end);

	}

	public synchronized Boolean getIncremental() {
		return incremental;
	}

	public synchronized void setIncremental(Boolean incremental) {
		this.incremental = incremental;
	}

	public synchronized FileHandler getFh() {
		return fh;
	}

	public synchronized void setFh(FileHandler fh) {
		this.fh = fh;
	}

	public synchronized GraphTraversal getQueryTraversal() {
		return queryTraversal;
	}

	public synchronized void setQueryTraversal(String query) {
		switch (query) {
		case "1":
			LOGGER.info("ProductPopularity");
			queryTraversal = graph.traversal().V().out("orders").out("contains").has("idProduct", "product 10");
			break;
		case "2":
			LOGGER.info("ProductPopularityC");
			queryTraversal = graph.traversal().V()
					.where(__.out("orders").out("contains").has("idProduct", "product 10"));
			break;
		case "3":
			LOGGER.info("AlternativeCustomer");
			List<String> idProducts = new ArrayList<String>();
			for (int i = 1; i <= 30; i++) {
				idProducts.add("product " + i);
			}
			queryTraversal = graph.traversal().V().hasLabel("Customer")
					.not(__.out("orders").out("contains").has("idProduct", P.within(idProducts)));
			break;
		case "4":
			LOGGER.info("PackagePopularity");
			queryTraversal = graph.traversal().V().and(__.out("orders").out("contains").has("idProduct", "product 10"),
					__.out("orders").out("contains").has("idProduct", "product 20"));
			break;
		case "5":
			LOGGER.info("SimProductsPopularity");
			queryTraversal = graph.traversal().V().or(__.out("orders").out("contains").has("idProduct", "product 10"),
					__.out("orders").out("contains").has("idProduct", "product 20"));
			break;
		case "6":
			LOGGER.info("PrefCustomer");
			queryTraversal = graph.traversal().V().has("idProduct", "product 10").in("contains").in("orders");
			break;
		case "7":
			LOGGER.info("PrefCustomerSimProducts");
			queryTraversal = graph.traversal().V().has("idProduct", P.within("product 10", "product 20")).in("contains")
					.in("orders");
			break;
		}
	}

	public synchronized Graph getSubgraph() {
		return subgraph;
	}

	public synchronized void setSubgraph(Graph subgraph) {
		this.subgraph = subgraph;
	}

	public synchronized Graph getGraph() {
		return graph;
	}

	public synchronized void setGraph(String fileName) throws IOException {
		graph = TinkerGraph.open();
		graph.io(IoCore.graphml()).readGraph(fileName);
	}

	public synchronized Set<Vertex> accessVertices() {
		Set<Vertex> copyVertices = vertices.stream().collect(Collectors.toSet());
		return copyVertices;
	}

	public synchronized void modifyVertices(Set<Vertex> newVertices) {
		vertices.addAll(newVertices);
	}

	public synchronized Long getRecords() {
		return records;
	}

	public synchronized void setRecords(Long records) {
		this.records = records;
	}

	// This method is being called by IncrementalAlgThread class
	public synchronized void callBack(String results) {
		System.out.println(results);
	}

	public synchronized void clearVertices(Set<Vertex> oldVertices) {

		vertices.removeAll(oldVertices);
	}

	@Override
	public void run() {
		try {
			startComputing();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}