package com.timetablingapp.jurusan;

import com.timetablingapp.common.base.BaseCrudService;
import com.timetablingapp.common.exception.ResourceNotFoundException;
import com.timetablingapp.jurusan.konsentrasi.KonsentrasiRepository;
import com.timetablingapp.jurusan.konsentrasi.KonsentrasiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JurusanService implements BaseCrudService<JurusanResponse, JurusanRequest, Integer> {

    private final JurusanRepository jurusanRepository;
    private final KonsentrasiRepository konsentrasiRepository;

    /**
     * Find all jurusans.
     * For admin users, returns all jurusans.
     * For faculty users, returns only jurusans in their faculty.
     *
     * Mirrors Laravel: Jurusan::getCurrent()
     *
     * @param faculty the faculty of the authenticated user (null for admin)
     */
    public List<JurusanResponse> findAllByFaculty(String faculty) {
        List<Jurusan> jurusans;
        if (faculty == null || faculty.isBlank()) {
            // Admin sees all
            jurusans = jurusanRepository.findAll();
        } else {
            // Faculty user sees only their faculty's jurusans
            jurusans = jurusanRepository.findByFaculty(faculty);
        }
        return jurusans.stream()
                .map(JurusanResponse::fromEntity)
                .toList();
    }

    /**
     * Get jurusan IDs for the authenticated user's faculty.
     * Admin gets all IDs, faculty user gets only their faculty's IDs.
     *
     * Mirrors Laravel: Jurusan::jurusanIds()
     */
    public List<Integer> getJurusanIds(String faculty) {
        List<Jurusan> jurusans;
        if (faculty == null || faculty.isBlank()) {
            jurusans = jurusanRepository.findAll();
        } else {
            jurusans = jurusanRepository.findByFaculty(faculty);
        }
        return jurusans.stream()
                .map(Jurusan::getId)
                .toList();
    }

    @Override
    public List<JurusanResponse> findAll() {
        return jurusanRepository.findAll().stream()
                .map(JurusanResponse::fromEntity)
                .toList();
    }

    @Override
    public JurusanResponse findById(Integer id) {
        Jurusan jurusan = jurusanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", id));
        return JurusanResponse.fromEntity(jurusan);
    }

    @Override
    @Transactional
    public JurusanResponse create(JurusanRequest request) {
        Jurusan jurusan = new Jurusan();
        jurusan.setName(request.getName());
        jurusan.setFaculty(request.getFaculty());
        jurusan.setJenjang(request.getJenjang());
        jurusan.setColor(request.getColor());

        Jurusan saved = jurusanRepository.save(jurusan);
        return JurusanResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public JurusanResponse update(Integer id, JurusanRequest request) {
        Jurusan jurusan = jurusanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", id));

        jurusan.setName(request.getName());
        jurusan.setFaculty(request.getFaculty());
        jurusan.setJenjang(request.getJenjang());
        jurusan.setColor(request.getColor());

        Jurusan saved = jurusanRepository.save(jurusan);
        return JurusanResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Jurusan jurusan = jurusanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", id));
        jurusanRepository.delete(jurusan);
    }

    /**
     * Get concentrations for a specific jurusan.
     * Mirrors Laravel: CourseController.getKonsentrasiByJurusan($id)
     */
    public List<KonsentrasiResponse> getKonsentrasiByJurusanId(Integer jurusanId) {
        // Verify jurusan exists
        jurusanRepository.findById(jurusanId)
                .orElseThrow(() -> new ResourceNotFoundException("Jurusan", "id", jurusanId));

        return konsentrasiRepository.findByJurusanId(jurusanId).stream()
                .map(KonsentrasiResponse::fromEntity)
                .toList();
    }
}
