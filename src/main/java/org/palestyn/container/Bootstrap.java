package org.palestyn.container;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.Persistence;

import org.jboss.resteasy.core.ResteasyDeploymentImpl;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.weld.environment.servlet.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

public class Bootstrap {

	final String CDI_INJECTION_FACTORY = "org.jboss.resteasy.cdi.CdiInjectorFactory";
	final String BIND_HOST;
	final int BIND_PORT;

	final String PU_NAME;
	
	private Container.Properties containerProperties;

	final static Logger logger = LoggerFactory.getLogger(Bootstrap.class);
	
	public static void main(String[] args) {
		long now = System.currentTimeMillis();
		new Bootstrap().boot();
		long millis = Duration.between(Instant.ofEpochMilli(now), Instant.now()).toMillis();

		logger.info("~~~ Startup time {} seconds", millis / 1000D);
	}

	public Bootstrap() {
		containerProperties=new Container.Properties();
		this.PU_NAME = containerProperties.getProperty("persistence.unit.name").get();
		this.BIND_HOST = containerProperties.getProperty("service.host").orElse("localhost");
		this.BIND_PORT = new Integer(containerProperties.getProperty("service.port").orElse("8080"));
	}
	
	private void boot() {
		
		String contextPath = containerProperties.getProperty("application.context.path").orElse("/");
		String deploymentName = containerProperties.getProperty("application.deployment.name").orElse("no-deployment-name");
		String applicationClass = containerProperties.getProperty("application.class").get();
		
		UndertowJaxrsServer server=new UndertowJaxrsServer();

		ResteasyDeployment deployment = new ResteasyDeploymentImpl();
        deployment.setInjectorFactoryClass(CDI_INJECTION_FACTORY);
        deployment.setApplicationClass(applicationClass);
        
        DeploymentInfo deploymentBuilder = server
				.undertowDeployment(deployment)
				.setDeploymentName(deploymentName)
				.setContextPath(contextPath)
				.setClassLoader(Bootstrap.class.getClassLoader())
				.addListener(Servlets.listener(Listener.class));
        
        Undertow.Builder undertowBuilder = Undertow.builder()
				.addHttpListener(BIND_PORT, BIND_HOST);

		logger.info("~~~ STARTING SERVER");

		server.start(undertowBuilder);

		setUpPersistenceContext();
		
		logger.info("~~~ BEGINNING DEPLOYMENT");
		server.deploy(deploymentBuilder);
		logger.info("Configured Listeners: {}", deploymentBuilder.getListeners());
		
		DeploymentManager deploymentManager = server.getManager();
		logger.info("~~~ DEPLOYMENT {}", deploymentManager.getState());		
		
		logger.info("~~~ READY TO SERVE");
	}

	private void setUpPersistenceContext() {
		URL persistenceContext = Bootstrap.class.getResource("/META-INF/persistence.xml");
		if(Objects.isNull(persistenceContext)) return;

		setupDataSource();
		logger.info("~~~ INITIALIZING JPA");
		Persistence.createEntityManagerFactory(PU_NAME);		
	}

	private void setupDataSource() {
		logger.info("~~~ SETTING UP JNDI DATASOURCE");
		
		HikariConfig config=new HikariConfig();
		
		config.setMaximumPoolSize(new Integer(containerProperties.getProperty("datasource.connections.max").get()));
		config.setMinimumIdle(new Integer(containerProperties.getProperty("datasource.connections.min").get()));
		config.setJdbcUrl(containerProperties.getProperty("datasource.jdbc.url").get());
		config.setDriverClassName(containerProperties.getProperty("datasource.driver").get());
		config.setUsername(containerProperties.getProperty("datasource.username").get());
		config.setPassword(containerProperties.getProperty("datasource.password").get());
		
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		config.addDataSourceProperty("useServerPrepStmts", "true");
		config.addDataSourceProperty("useLocalSessionState", "true");
		config.addDataSourceProperty("rewriteBatchedStatements", "true");
		config.addDataSourceProperty("cacheResultSetMetadata", "true");
		config.addDataSourceProperty("cacheServerConfiguration", "true");
		config.addDataSourceProperty("elideSetAutoCommits", "true");
		config.addDataSourceProperty("maintainTimeStats", "true");
	    
		InitialContext ic;
		try {
			ic = new InitialContext();
			ic.createSubcontext("java:/comp/env/jdbc");
			ic.bind("java:/comp/env/jdbc/datasource", new HikariDataSource(config));
		} catch (NamingException e) {
			throw new RuntimeException(e);
		}
	}
}
