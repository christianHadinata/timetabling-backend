package com.timetablingapp.schedule.algorithm.constraint.activity;

import org.springframework.stereotype.Component;

import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.AlgorithmCourse;
import com.timetablingapp.schedule.algorithm.model.GAContext;

/** Curriculum "bentrok" — same tingkat/konsentrasi grouping. */
@Component
public class CourseConflict implements ActivityPairConstraint {
    @Override public boolean isConflict(int a1, int a2, GAContext ctx) {
        AlgorithmActivity x = ctx.getActivityByIdx(a1), y = ctx.getActivityByIdx(a2);
        AlgorithmCourse cx = x.getCourse(), cy = y.getCourse();
        String kx = cx.getKonsentrasi() == null ? "" : cx.getKonsentrasi();
        String ky = cy.getKonsentrasi() == null ? "" : cy.getKonsentrasi();
        boolean sameTingkat = cx.getTingkat() == cy.getTingkat();
        boolean sameKons    = kx.equals(ky);

        // Wajib vs Wajib: same tingkat, same konsentrasi, same class → bentrok
        if ("Wajib".equals(cx.getType()) && "Wajib".equals(cy.getType())
                && sameTingkat && sameKons && x.getCourseClass().equals(y.getCourseClass())) {
            return true;
        }
        // Wajib vs Pilihan (either order): same tingkat, same konsentrasi → bentrok
        boolean mixed = ("Wajib".equals(cx.getType()) && "Pilihan".equals(cy.getType()))
                     || ("Pilihan".equals(cx.getType()) && "Wajib".equals(cy.getType()));
        return mixed && sameTingkat && sameKons;
    }
}
