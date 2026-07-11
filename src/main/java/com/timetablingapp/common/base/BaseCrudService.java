package com.timetablingapp.common.base;

import java.util.List;

/**
 * Generic CRUD service interface.
 *
 * @param <RES> Response DTO type
 * @param <REQ> Request DTO type
 * @param <ID>  Entity ID type
 */
public interface BaseCrudService<RES, REQ, ID> {

    /**
     * Retrieve all entities as response DTOs.
     */
    List<RES> findAll();

    /**
     * Retrieve a single entity by its ID.
     *
     * @throws com.timetablingapp.common.exception.ResourceNotFoundException if not found
     */
    RES findById(ID id);

    /**
     * Create a new entity from the request DTO.
     */
    RES create(REQ request);

    /**
     * Update an existing entity by ID with the request DTO.
     *
     * @throws com.timetablingapp.common.exception.ResourceNotFoundException if not found
     */
    RES update(ID id, REQ request);

    /**
     * Delete an entity by its ID.
     *
     * @throws com.timetablingapp.common.exception.ResourceNotFoundException if not found
     */
    void delete(ID id);
}
