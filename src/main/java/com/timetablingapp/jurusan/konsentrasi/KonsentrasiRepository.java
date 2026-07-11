package com.timetablingapp.jurusan.konsentrasi;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KonsentrasiRepository extends JpaRepository<Konsentrasi, Integer> {

    /**
     * Find all concentrations for a specific jurusan.
     * Mirrors Laravel: Konsentrasi::where('jurusan_id', $id)->get()
     */
    List<Konsentrasi> findByJurusanId(Integer jurusanId);
}
