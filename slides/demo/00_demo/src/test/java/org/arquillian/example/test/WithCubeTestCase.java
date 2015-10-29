package org.arquillian.example.test;
import java.math.BigDecimal;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

import org.arquillian.example.domain.Beer;
import org.arquillian.example.domain.BeerBuilder;
import org.arquillian.example.domain.Brewery;
import org.arquillian.example.domain.Country;
import org.arquillian.example.domain.Type;
import org.arquillian.example.repository.BeerRepository;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class WithCubeTestCase {

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage(Beer.class.getPackage())
                .addPackage(BeerRepository.class.getPackage())
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml");
    }

    @Inject
    private UserTransaction utx;

    @Inject
    private EntityManager em;
    
    @Inject
    private BeerRepository repository;
    
    @Before
    public void preparePersistenceTest() throws Exception {
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
    public void should_remove_beer() throws Exception {
        Beer beer = repository.getById(3L);
        repository.delete(beer);
        
        Assert.assertNull(repository.getById(3L));
    }
    
    @After
    public void commitTransaction() throws Exception {
        utx.commit();
    }
}