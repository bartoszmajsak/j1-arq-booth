package org.arquillian.example.repository;

import org.arquillian.example.domain.Beer;

public interface BeerRepository {

    void delete(Long id);

    void delete(Beer beer);

    Beer getById(Long id);

}