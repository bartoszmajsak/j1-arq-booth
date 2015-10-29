package org.arquillian.example.test;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.transaction.UserTransaction;

import org.arquillian.example.domain.Beer;
import org.arquillian.example.domain.BeerBuilder;
import org.arquillian.example.domain.Brewery;
import org.arquillian.example.domain.Country;
import org.arquillian.example.domain.Type;
import org.arquillian.example.repository.BeerRepository;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

public class CompleteTestCase {

    public static final String TAG_SEPARATOR = ":";
    private static DockerClient dockerClient;
    private static List<String> imageIds = new ArrayList<String>();
    
    // Container
    private static ServerDeploymentHelper deployer;

    // APE
    private UserTransaction utx;
    private EntityManager em;
    
    @BeforeClass
    public static void connectToDocker() throws Exception {

        // Configures and connect to docker host
        DockerClientConfig.DockerClientConfigBuilder configBuilder =
            DockerClientConfig.createDefaultConfigBuilder();

        // Need to check where the docker host is.
        // Can be Linux or MacOS (withBoot2Docker or DockerMachine)
        String dockerServerUri = "http://dockermachineIp:port";

        String version = "1.16";

        // Connect to docker host
        URI dockerUri = URI.create(dockerServerUri);
        configBuilder.withVersion(version).withUri(dockerUri.toString());
        dockerClient = DockerClientBuilder.getInstance(configBuilder.build()).build();


        // Create and Start required containers

        // Start MySql
        final Map<Integer, Integer> dbPortBinding = new HashMap<Integer, Integer>();
        dbPortBinding.put(81, 81);
        dbPortBinding.put(1521, 1521);
        final CreateContainerResponse database = createImage("mysql:latest", "database", dbPortBinding);
        startContainer(database.getId());
        waitUntilContainerIsStarted();
        imageIds.add(database.getId());

        // Start Wildfly
        String wildflyId = buildImage("src/test/java/wildfly");
        final Map<Integer, Integer> wildflyPortBinding = new HashMap<Integer, Integer>();
        wildflyPortBinding.put(8081, 8080);
        wildflyPortBinding.put(9991, 9990);
        final CreateContainerResponse wildfly = createImage(wildflyId, "Wildfly", wildflyPortBinding, "database:database");
        startContainer(wildfly.getId());
        waitUntilContainerIsStarted();
        imageIds.add(wildfly.getId());
        
        final String username = "admin";
        final String password = "Admin#70365";

        ModelControllerClient modelControllerClient = null;
        try {
            modelControllerClient = ModelControllerClient.Factory.create(
                    "http",
                    "localhost",
                    9991,
                    new CallbackHandler() {
                        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                            for (Callback current : callbacks) {
                                if (current instanceof NameCallback) {
                                    NameCallback ncb = (NameCallback) current;
                                    ncb.setName(username);
                                } else if (current instanceof PasswordCallback) {
                                    PasswordCallback pcb = (PasswordCallback) current;
                                    pcb.setPassword(password.toCharArray());
                                } else if (current instanceof RealmCallback) {
                                    RealmCallback rcb = (RealmCallback) current;
                                    rcb.setText(rcb.getDefaultText());
                                } else {
                                    throw new UnsupportedCallbackException(current);
                                }
                            }
                        }
                    });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        File tempDeploymentFile = File.createTempFile("deploy", ".war");
        ZipOutputStream deployment = new ZipOutputStream(new FileOutputStream(tempDeploymentFile));
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/Beer.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/Beer.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/BeerBuilder.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/BeerBuilder.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/Brewery.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/Brewery.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/Country.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/Country.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/Type.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/Type.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/repository/BeerCriteria.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/repository/BeerCriteria.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/repository/BeerRepository.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/repository/BeerRepository.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/repository/JpaBeerRepository.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/repository/JpaBeerRepository.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/service/BeerService.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/service/BeerService.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/org/arquillian/example/service/BeerInserter.class"));
        deployment.write(Files.readAllBytes(Paths.get("target/classes/org/arquillian/example/service/BeerInserter.class")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/classes/META-INF/persistence.xml"));
        deployment.write(Files.readAllBytes(Paths.get("src/test/resources/test-persistence.xml")));
        deployment.closeEntry();
        deployment.putNextEntry(new ZipEntry("/WEB-INF/beans.xml"));
        deployment.write(Files.readAllBytes(Paths.get("src/main/resources/META-INF/beans.xml")));
        deployment.closeEntry();
        
        deployer = new ServerDeploymentHelper(modelControllerClient);
        deployer.deploy("my-test.war", new FileInputStream(tempDeploymentFile));
    }



    private static String buildImage(String dockerfileDirectory) {
        String tag = UUID.randomUUID().toString();
        BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(new File(dockerfileDirectory));
        buildImageCmd.withTag(tag);
        buildImageCmd.exec();
        
        return tag;
    }

    private static CreateContainerResponse createImage(String image, String name, Map<Integer, Integer> portBinding, String... links) {

        // Exposed ports and port bindings.
        ExposedPort[] exposedPorts = new ExposedPort[portBinding.size()];
        Ports ports = new Ports();

        final Set<Map.Entry<Integer, Integer>> entries = portBinding.entrySet();
        int i = 0;
        for (Map.Entry<Integer, Integer> port : entries) {
            exposedPorts[i] = ExposedPort.tcp(port.getValue());
            ports.bind(exposedPorts[i], Ports.Binding(port.getKey()));
            i++;
        }

        // Links
        Link[] dockerLinks = new Link[links.length];

        int j = 0;
        for (String link : links) {
            final String[] split = link.split(":");
            dockerLinks[j] = new Link(split[0], split[1]);
            j++;
        }

        // Create the container
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(image);
        return createContainerCmd.withName(name)
                .withExposedPorts(exposedPorts)
                .withPortBindings(ports)
                .withLinks(dockerLinks).exec();
    }

    private static void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    private static void waitUntilContainerIsStarted() {
    }
    
    @Before
    public void preparePersistenceTest() throws Exception {
        utx = (UserTransaction)new InitialContext().lookup("javax:/UserTransaction");
        em = (EntityManager)new InitialContext().lookup("javax:/EntityManager");
        clearDatabase();
        insertData();
        startTransaction();
    }

    private void clearDatabase() throws Exception {
        utx.begin();
        em.joinTransaction();
        em.createQuery("delete from Beer").executeUpdate();
        em.createQuery("delete from Brewery").executeUpdate();
        utx.commit();
    }

    private void insertData() throws Exception {
        utx.begin();
        em.joinTransaction();
        
        Beer mocnyFull = BeerBuilder.create()
                                    .named("Mocny Full")
                                    .withPrice(BigDecimal.valueOf(1.0))
                                    .havingAlcohol(BigDecimal.valueOf(4.5))
                                    .from(new Brewery("Kiepski Browar", Country.POLAND))
                                    .ofType(Type.LAGER)
                                    .withCode("mocny_full")
                                    .build();
       em.persist(mocnyFull);

       Brewery brewDog = new Brewery("Brew Dog", Country.SCOTLAND);
       Beer endOfHistory = BeerBuilder.create()
                                      .named("End of history")
                                      .withPrice(BigDecimal.valueOf(765.0))
                                      .havingAlcohol(BigDecimal.valueOf(55.0))
                                      .from(brewDog)
                                      .ofType(Type.BLOND_ALE)
                                      .withCode("end_of_history")
                                      .build();

       Beer bismarck = BeerBuilder.create()
                                  .named("Sink The Bismarck!")
                                  .withPrice(BigDecimal.valueOf(64.0))
                                  .havingAlcohol(BigDecimal.valueOf(41.0))
                                  .from(brewDog)
                                  .ofType(Type.QUADRUPEL_IPA)
                                  .withCode("bismarck")
                                  .build();

       em.persist(endOfHistory);
       em.persist(bismarck);

       Beer delirium = BeerBuilder.create()
                                  .named("Delirium Tremens")
                                  .withPrice(BigDecimal.valueOf(10.0))
                                  .havingAlcohol(BigDecimal.valueOf(8.5))
                                  .from(new Brewery("Brouwerij Huyghe", Country.BELGIUM))
                                  .ofType(Type.PALE_ALE)
                                  .withCode("delirium")
                                  .build();
       em.persist(delirium);

       Beer kwak = BeerBuilder.create()
                              .named("Pauwel Kwak")
                              .withPrice(BigDecimal.valueOf(4.0))
                              .havingAlcohol(BigDecimal.valueOf(8.4))
                              .from(new Brewery("Brouwerij Bosteels", Country.BELGIUM))
                              .ofType(Type.AMBER)
                              .withCode("kwak")
                              .build();
       em.persist(kwak);

       utx.commit();
    }
    
    private void startTransaction() throws Exception {
        utx.begin();
    }
    
    @Test
    public void should_remove_beer_but_not_brewery() throws Exception {
        BeanManager bm = (BeanManager)new InitialContext().lookup("javax:BeanManager");
        Bean<BeerRepository> repositoryBean = (Bean<BeerRepository>)bm.resolve(bm.getBeans(BeerRepository.class));
        BeerRepository repository = (BeerRepository)repositoryBean.create(null);
        
        Beer beer = repository.getById(3L);
        repository.delete(beer);
        
        Assert.assertNull(repository.getById(3L));
        
        repositoryBean.destroy(repository, null);
    }
    
    @After
    public void commitTransaction() throws Exception {
        utx.commit();
    }
    

    @AfterClass
    public static void stopConnection() throws Exception {
        deployer.undeploy("my-test.war");
        for (String imageId : imageIds) {
            stopContainer(imageId);
            removeContainer(imageId);
        }
        dockerClient.close();
    }

    private static void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    private static void removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).exec();
    }

}