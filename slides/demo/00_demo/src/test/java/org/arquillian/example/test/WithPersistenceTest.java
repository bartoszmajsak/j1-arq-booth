package org.arquillian.example.test;

import javax.inject.Inject;

import org.arquillian.example.domain.Beer;
import org.arquillian.example.repository.BeerRepository;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.persistence.ShouldMatchDataSet;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class WithPersistenceTest {

    @Deployment
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "test.war")
                .addPackage(Beer.class.getPackage())
                .addPackage(BeerRepository.class.getPackage())
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml");
    }

    @Inject
    private BeerRepository beerRepository;

    @Test
    @UsingDataSet("beers.yml")
    @ShouldMatchDataSet("beers-without-bismarck.yml")
    public void should_remove_beer() throws Exception {
        // given
        Beer bismarck = beerRepository.getById(3L);

        // when
        beerRepository.delete(bismarck);
    }
}
