package com.dtcookie.shop.backend;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.dtcookie.database.Database;
import com.dtcookie.shop.Product;
import com.dtcookie.util.Http;
import com.dtcookie.util.Otel;
import com.sun.net.httpserver.HttpExchange;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class BackendServer {

	private static final Logger log = LogManager.getLogger(BackendServer.class);

	public static final int CREDIT_CARD_LISTEN_PORT = 54040;
	public static final int INVENTORY_LISTEN_PORT = 54041;
	public static final int CREDIT_CARD_SCAN_LISTEN_PORT = 54042;

	private static OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
	private static final Tracer tracer = openTelemetry.getTracer("manual-instrumentation");

	private static final ExecutorService executor = Executors.newCachedThreadPool();
	private static final Timer creditCardFullScanTimer = new Timer(true);
	private static final Random RAND = new Random(System.currentTimeMillis());

	public static void submain(String[] args) throws Exception {
		creditCardFullScanTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				log.info("PERFORMING FULL CREDIT CARD SCAN");
				Http.JDK.POST("http://localhost:" + BackendServer.CREDIT_CARD_SCAN_LISTEN_PORT + "/full-credit-card-scan", UUID.randomUUID().toString());	
			}
		}, 1000 * 20, 1000 * 60 * 15);

		Database.Debug.set(true);
		log.info("Launching Backend Server");
		openTelemetry = Otel.init();
		Http.serve(CREDIT_CARD_LISTEN_PORT, "/validate-credit-card", BackendServer::handleCreditcards);
		Http.serve(INVENTORY_LISTEN_PORT, "/check-inventory", BackendServer::handleInventory);
		Http.serve(CREDIT_CARD_SCAN_LISTEN_PORT, "/full-credit-card-scan", BackendServer::fullCreditCardScan);
	}

	public static UUID process(Product product) throws Exception {
		Span span = tracer.spanBuilder("process").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			span.setAttribute("product.name", product.getName());
			BackendQueries.execute("SELECT pattern FROM credit_card_patterns WHERE vendor = '" + product.getID() + "'");
			for (int i = 0; i < 1 + ThreadLocalRandom.current().nextLong(1); i++) {
				executor.submit(BackendServer::postProcess);
			}
			notifyProcessingBackend(product);
			return UUID.randomUUID();
		} catch (Exception e) {
			span.recordException(e);
			span.setStatus(StatusCode.ERROR);
			throw e;
		} finally {
			span.end();
		}
	}

	public static void notifyProcessingBackend(Product product) throws Exception {
//		GETRequest request = new GETRequest("http://localhost:8090/quote");
//		// GETRequest request = new GETRequest("http://<replace with remote IP address>/app");
//		request.send();
	}
	
	public static UUID fullCreditCardScan(HttpExchange exchange) throws Exception {
		log.info("PERFORMING FULL CREDIT CARD SCAN");
		UUID result = UUID.randomUUID();
		for (int i = 0; i < 35; i++) {
			executor.submit(BackendServer::performFullCreditCardScan);
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				return result;
			}
		}
		return result;
	}

	public static UUID handleCreditcards(HttpExchange exchange) throws Exception {
		String requestURI = exchange.getRequestURI().toString();
		String productID = requestURI.substring(requestURI.lastIndexOf("/") + 1);
		UUID result = process(Product.random());
		executor.submit(Purchase.confirm(productID));
		return result;
	}

	public static String handleInventory(HttpExchange exchange) throws Exception {
		String url = exchange.getRequestURI().toString();
		String productName = url.substring(url.lastIndexOf("/"));
		int quantity = 1;				
		BackendQueries.execute("SELECT * FROM products WHERE name = '" + productName + "'");
		checkStorageLocations(productName, quantity);
		return "done";
	}

	public static void checkStorageLocations(String productName, int quantity) {
		Span span = tracer.spanBuilder("check-storage-locations").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			for (StorageLocation location : StorageLocation.getAll()) {
				if (location.available(productName, quantity)) {
					deductFromLocation(location, productName, quantity);
					break;
				}
			}
		} finally {
			span.end();
		}
	}

	public static void deductFromLocation(StorageLocation location, String productName, int quantity) {
		location.deduct(productName, quantity);
	}

	public static Object postProcess() throws Exception {
		Span span = tracer.spanBuilder("post-process").setSpanKind(SpanKind.INTERNAL).startSpan();
		try (Scope scope = span.makeCurrent()) {
			BackendQueries.execute("SELECT * FROM inventory WHERE product = '" + UUID.randomUUID().toString() + "'");
		} catch (Exception e) {
			span.setStatus(StatusCode.ERROR);
			span.recordException(e);
			throw e;
		} finally {
			span.end();
		}
		return null;
	}

	public static Object performFullCreditCardScan() throws Exception {
		long start = System.currentTimeMillis();
		try (Connection con = Database.getConnection(60, TimeUnit.SECONDS)) {
			Thread.sleep(50 * (System.currentTimeMillis() - start));
			if (con != null) {
				try (Statement stmt = con.createStatement()) {
					StringBuilder sb = new StringBuilder();					
					sb = sb.append(RAND.nextInt(0, 10)).append(RAND.nextInt(0, 10)).append(RAND.nextInt(0, 10)).append(RAND.nextInt(0, 10));
					sb = sb.append("-####").append("-####").append("-####");
					String ccNumber = sb.toString();
					log.info("Full Scan of Credit Card: " + ccNumber);
					stmt.executeUpdate(
							"SELECT * FROM credit_card WHERE number = '" + ccNumber + "'");
				}
			} else {
				executor.submit(BackendServer::performFullCreditCardScan);
			}
		}
		return null;
	}

}
