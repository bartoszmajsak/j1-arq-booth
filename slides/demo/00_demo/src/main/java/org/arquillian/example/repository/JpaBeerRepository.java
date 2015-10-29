package org.arquillian.example.repository;

import javax.enterprise.context.RequestScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.arquillian.example.domain.Beer;

@RequestScoped
public class JpaBeerRepository implements BeerRepository {
    @PersistenceContext
    private EntityManager em;

    @Override
    public void delete(Beer beer) {
        beer.getBrewery().remove(beer);
        em.remove(beer);
    }

    @Override
    public void delete(Long id) {
        em.remove(getById(id));
    }

    @Override
    public Beer getById(Long id) {
        return em.find(Beer.class, id);
    }
}