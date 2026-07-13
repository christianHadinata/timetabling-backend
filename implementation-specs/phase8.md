# Phase 8 — Genetic Algorithm & Timetable: `schedule/algorithm/`

> **Document Version:** 1.0
> **Date:** 2026-07-13
> **Depends on:** Phases 1–7 (config/common, activity/, room/, lecturer/, result/, setting/, semester/, **schedule/slot/** — the `slot_acts` search space)
> **Reference roadmap:** [migration-roadmap.md](migration-roadmap.md) §Phase 8
> **Legacy sources:**
> - Partial Java rewrite (read-only): `timetabling_laravel/genetic/timetablingapp/src/main/java/com/genetic/timetablingapp/schedule/algorithm/**`
> - Laravel orchestration: `app/Http/Controllers/TableController.php` (`getData`, `saveData`, `getAlgorithm`, `getInitSchedule`, `showSemesterData`, `prepareScheduleData`, `createJSONRequest2`)
> - Laravel SSE: `app/Http/Controllers/SocketController.php` (`sse`)

---

## 1. Goal

Port the **genetic algorithm** that assigns each unscheduled activity to a set of consecutive slots, plus the **timetable orchestration** endpoints and **real-time progress streaming** via Server-Sent Events (SSE).

Concretely this phase delivers:

1. A self-contained GA package (`schedule/algorithm/genetic/`) ported from the partial `genetic/` rewrite — **with the compile-blocking bugs and NPEs in that rewrite fixed** (see §3) and the three stubbed hard constraints (`LecturerConflict`, `CourseConflict`, `CourseClassConflict`) actually implemented from the commented-out Laravel logic in `Activity.php`.
2. A **`TimetableService`** that maps our JPA entities → GA models, runs the GA asynchronously, and maps the GA `Result` back to the frontend's `{ inserted, conflicts, notInserted }` shape.
3. A **`TimetableController`** exposing the timetable-page/data/generate/save endpoints (ports of `TableController`'s web routes).
4. An **`SseController` + `SseService`** streaming `{ trial, generation, bestFitness, progress% }` events while the GA runs — replacing Laravel's TCP-socket bridge with an in-process listener (the GA now runs inside the same JVM, so no socket is needed).

### What Phase 7 already gave us (do not re-implement)

The Laravel `getAlgorithm` flow calls `Activity::generateValidateSlots` / `generateSlots` inside `createJSONRequest2` to compute, per activity, the list of valid **start slots + priority**. **Phase 7 already materialises exactly this into the `slot_acts` table** (`SlotValidationService.revalidate`). So the GA's search space per activity is simply *its `slot_acts` rows* — we read them via `SlotActivityRepository`, we do **not** re-run slot validation here.

---

## 2. Key architectural decisions

| # | Decision | Rationale |
|---|----------|-----------|
| **A1** | **Search space = `slot_acts`.** Each `SlotActivity` row = one candidate *start* slot + priority for its activity. `SlotsWithPriority.resolveSlots()` expands a start-slot id into `duration` consecutive slot ids (`startId … startId+duration-1`). | Matches `SlotsWPriority.resolveSlots` + `GAContext.getSlotStartToDuration`, and the seeded, room-major/day-major/hour-major slot id layout relied on by `TableController::getResultTimeIds` (`id = (day-1)*17 + 1 + hour - 7`). |
| **A2** | **GA collaborators split into singletons vs per-run.** Stateless beans: the `Constraint` list, `FitnessFunction`, `Crossover`, `Mutation`, `Selection`. Per-run (constructed fresh each `generate`): `GAContext`, `Problem`, `GeneticAlgorithm`, `Chromosome`, `Population`, `SlotUsage`. A **`GaEngineFactory`** bean assembles a `GeneticAlgorithm` for a given `Problem` + progress listener. | The genetic rewrite annotated `GAContext`/`Problem`/`GeneticAlgorithm` with `@Service` **and** gave them constructors requiring runtime data — those can't coexist as singletons. `GAContext` holds per-request activities/slots, so it must be per-run. Constraints read their data from `chromosome.getProblem().getContext()` at evaluate time, so they stay stateless singletons. |
| **A3** | **SSE is in-process.** `SocketController@sse` opened a TCP socket to a *separate* Java GA service (port 81/8085) and relayed its stdout. Here the GA runs in the same Spring app, so a `GaProgressListener` callback pushes events straight into `SseService`, which fans them out to registered `SseEmitter`s. | Eliminates the Guzzle `POST /rest/call` HTTP hop (`TableController::getAlgorithm` line 202) and the raw-socket bridge entirely. |
| **A4** | **`generate` is `@Async`; the HTTP call returns immediately** with a `jobId`; progress + final result arrive over SSE. A synchronous fallback (`?wait=true`) returns the `GenerateResponse` directly for tooling/tests. | Mirrors the Laravel UX (POST kicks off, `/sse` streams). Keeps the request thread free for long runs. |
| **A5** | **Types: `Integer`/`int` throughout the algorithm models**, matching the new backend (`slots`, `activities`, `rooms` are `INT unsigned`). The genetic rewrite mixed `Long`/`long`/`int` inconsistently (a compile error in `SlotUsage`). | Consistency with Phase 1–7 entities (all `Integer` ids except `validate_lock`). |
| **A6** | **Faithful, not aspirational.** We port only the constraints the Laravel algorithm actually enforced. `LecturerMovingConstraint` and `RoomIdleConstraint` are **soft** constraints left unimplemented (`throw UnsupportedOperationException`) in the rewrite — we register them as **no-op soft constraints returning 0** so the bean list wires cleanly, and mark them `// TODO Phase 8+`. | Avoids inventing behaviour that never existed. |

---

## 3. Bugs in the partial rewrite that MUST be fixed during the port

These are present in `genetic/timetablingapp/**` and would either not compile or NPE at runtime. The ported files below already incorporate the fixes; this table is the audit trail.

| Source file | Defect | Fix |
|-------------|--------|-----|
| `models/io/GAContext.java` | `activityIndexById`/`slotIndexById`/`roomIndexById` used in `init()` but never instantiated → **NPE**. Also `@Service` on a class with a data-carrying constructor. | Instantiate the three maps in the constructor; drop `@Service` (per-run object, built by `TimetableService`). |
| `models/evaluate/SlotUsage.java` | `slotActivities` never instantiated → NPE. `int[] slotIds = gene.getSlotIds()` but `Gene.slotIds` is `long[]` → **won't compile**. Loop `for(i=0;i<=slotIds.length;i++)` → **off-by-one** (ArrayIndexOOB). | Init `slotActivities = new ArrayList<>()`; type ids as `int[]`/`Integer`; loop `i < slotIds.length`. |
| `genetic/GeneticAlgorithm.java` | `compareChromosomeFitness` dereferences `bestChromosome` while it is still `null` (first call from `initialPopulationGeneration`) → **NPE**. Comparison `compareTo(...) == 1` keeps the **worse** chromosome (our `FitnessVector` is a *minimisation* vector: fewer violations ⇒ "smaller"). | Null-guard: first chromosome becomes best; replace when `c.getFitness().compareTo(best.getFitness()) < 0`. |
| `genetic/Chromosome.java` | `copy()` calls `this.fitness.copy()` unconditionally; a chromosome fresh from `createValidChromosome()` (fitness not yet set) → NPE when copied. | `c.fitness = (this.fitness != null) ? this.fitness.copy() : null;` |
| `models/constraint/ActivityConstraint/{LecturerConflict,CourseConflict,CourseClassConflict}.java` | All three `return false` (stubs) → the hard "activity conflict" constraint never fires; GA would happily double-book a lecturer. | Implement from the commented-out `Activity::isSameLecturer/isCourseConflict/isSameCourseClass` logic (§7.3). Requires `AlgorithmActivity` to carry `lecturerNiks`, `course_class`, and an `AlgorithmCourse{code,type,tingkat,konsentrasi}`. |
| `models/constraint/{LecturerMovingConstraint,RoomIdleConstraint}.java` | Every method `throw UnsupportedOperationException` → Spring can't even register them as `Constraint` beans without them blowing up when evaluated. | Convert to no-op **soft** constraints: `isHard()=false`, `getWeight()=0`, `evaluate()` returns `new ConstraintResult(0,0,Set.of())`. Keep the day/hour grouping stubs as `// TODO Phase 8+`. |
| `ScheduleService.java` (mapping) | Uses genetic-project getters that **don't exist** in our entities: `activity.getSemester_id()`, `getCourse_class()`, `getCourse_session()`, `room.getRoom_code()`, `activity.getSlotActs()`. | Rewrite the mapping in `TimetableService` against our getters: `getSemester().getId()`, `getCourseClass()`, `getCourseSession()`, `getRoomCode()`, and `slotActivityRepository.findByActivity_Id(...)`. |
| `genetic/problem/Problem.java` | `@PostConstruct init()` on a `@Service` — but `Problem` needs a runtime `GAContext`, so it can't be a singleton. | Drop `@Service`/`@PostConstruct`; construct `new Problem(context)` per run and call `init()` explicitly. |

---

## 4. File inventory

### 4.1 Files to **ADD**

```
src/main/java/com/timetablingapp/schedule/algorithm/
├── TimetableController.java              # REST: page/data/generate/save/schedule
├── TimetableService.java                # entity→model mapping, GA orchestration (@Async), model→response
├── SseController.java                   # GET /api/sse/progress  (SseEmitter)
├── SseService.java                      # emitter registry + fan-out
├── dto/
│   ├── GenerateRequest.java             # { settingId, lockedSchedules[] }
│   ├── GenerateResponse.java            # { jobId } async  |  { inserted, conflicts, notInserted } sync
│   ├── SaveTimetableRequest.java        # { schedules[], notInserted[] }
│   ├── ScheduleDto.java                 # { activityId, roomId, day, startTime, endTime }
│   ├── TimetableDataResponse.java       # getData() payload (activities+rooms maps)
│   ├── ScheduleDataResponse.java        # prepareScheduleData(): { inserted[], notInserted[] }
│   └── ProgressEvent.java              # SSE payload { jobId, status, trial, generation, hardViolations, softPenalty, progress }
└── genetic/
    ├── GeneticAlgorithm.java            # per-run engine (fixed)
    ├── GaEngineFactory.java             # NEW: assembles GeneticAlgorithm(problem, listener)
    ├── GaProgressListener.java          # NEW: functional callback
    ├── Chromosome.java
    ├── Gene.java
    ├── Population.java
    ├── operators/
    │   ├── Crossover.java
    │   ├── Mutation.java
    │   └── Selection.java
    ├── problem/
    │   ├── Problem.java
    │   ├── FitnessFunction.java
    │   ├── FitnessFunctionFactory.java
    │   └── FitnessVector.java
    ├── model/
    │   ├── GAContext.java
    │   ├── AlgorithmActivity.java
    │   ├── AlgorithmCourse.java
    │   ├── AlgorithmRoom.java
    │   ├── AlgorithmSlot.java
    │   ├── AlgorithmTime.java
    │   ├── SlotsWithPriority.java
    │   ├── SlotUsage.java
    │   └── SlotActivityUsage.java        # (was models/evaluate/SlotActivity.java — renamed to avoid clash with JPA SlotActivity)
    ├── constraint/
    │   ├── Constraint.java
    │   ├── ConstraintResult.java
    │   ├── ConflictedActivityConstraint.java
    │   ├── ConflictedSlotsConstraint.java
    │   ├── LecturerMovingConstraint.java   # no-op soft
    │   ├── RoomIdleConstraint.java         # no-op soft
    │   └── activity/
    │       ├── ActivityPairConstraint.java
    │       ├── CourseClassConflict.java    # implemented
    │       ├── CourseConflict.java         # implemented
    │       └── LecturerConflict.java       # implemented
    └── io/
        ├── AlgorithmResult.java
        └── Schedule.java

src/main/java/com/timetablingapp/config/
└── AsyncConfig.java                     # NEW: @EnableAsync + task executor for the GA
```

> **Naming:** the roadmap's `AlgorithmActivity/AlgorithmRoom/AlgorithmSlot/AlgorithmTime/AlgorithmCourse` prefix is deliberately kept so these GA models never collide with the JPA entities `Activity`/`Room`/`Slot`/`Time`/`Course`. The genetic rewrite reused bare names (`models/Activity.java`), which is why it needed fully-qualified names everywhere — we avoid that. Likewise `models/evaluate/SlotActivity.java` → **`SlotActivityUsage.java`** so it doesn't clash with the JPA `schedule.slot.act.SlotActivity`.

### 4.2 Files to **EDIT**

| File | Change |
|------|--------|
| `schedule/slot/act/SlotActivityRepository.java` | Add `List<SlotActivity> findByActivity_Id(Integer activityId);` (search space per activity). |
| `activity/constraint/ActivityConstraintRepository.java` | Add `List<ActivityConstraint> findByActivity_Id(Integer activityId);` (lecturer niks / room ids per activity for the GA models). |
| `activity/ActivityRepository.java` | Add `List<Activity> findBySemester_IdAndCourse_Jurusan_IdIn(...)` already exists; add a fetch-join variant `@Query("… join fetch a.course join fetch a.activityType where a.semester.id = :sid")` `List<Activity> findAllForScheduling(Integer sid)` to avoid N+1 when mapping. |
| `config/SecurityConfig.java` | Permit the SSE endpoint under ADMIN and, if using an `EventSource` that can't send the `Authorization` header, allow a `?token=` query param (see §11 Risk R3). Ensure `/api/sse/**` and `/api/timetable/**` require `hasRole('ADMIN')` except `GET /api/timetable/schedule/**` and `/api/timetable/data` which are `authenticated()`. |
| `result/ResultService.java` | Reuse existing `create`/`deleteBySemester`; `TimetableService.save()` calls into it (or directly `ResultRepository`) — no new logic, just confirm `deleteBySemester_Id` + bulk `create` are reachable. |

### 4.3 Files to **DELETE**

None. Phase 8 is additive. (The genetic project under `timetabling_laravel/genetic/` is read-only reference and is **not** copied wholesale — only ported file-by-file into `com.timetablingapp.schedule.algorithm`.)

---

## 5. End-to-end data flow

```
POST /api/timetable/generate  { settingId, lockedSchedules[] }
        │
        ▼
TimetableService.generate(req)                         ── returns { jobId } immediately (@Async)
  1. semesterId = current semester
  2. setting    = SettingService.findDetail(settingId)         → constraint sets (CUSTOM_ACTIVITY, ACTIVITY_TYPE, JURUSAN, ROOM_TYPE, ROOM_OWNER, WAKTU, HARI)
  3. activities = ActivityRepository.findAllForScheduling(semesterId)
                    .filter(by setting: activity id ∈ CUSTOM_ACTIVITY, type ∈ ACTIVITY_TYPE, jurusan ∈ JURUSAN)
  4. for each activity:  slotActs = SlotActivityRepository.findByActivity_Id(id)
                          .filter(by setting: slot.room.type ∈ ROOM_TYPE, slot.room ∈ ROOM_OWNER, slot.time.hour ∈ WAKTU, slot.time.day ∈ HARI)
                          → List<SlotsWithPriority>(startSlotId, priority)
  5. slots  = SlotRepository.findAllBy()          → List<AlgorithmSlot>
     rooms  = RoomRepository.findAll()            → List<AlgorithmRoom>
  6. initialSchedules = map(req.lockedSchedules)  → List<Schedule>   (already-placed, GA must respect)
  7. GAContext ctx = new GAContext(activities, slots, rooms, initialSchedules); ctx.init();
  8. Problem problem = new Problem(ctx); problem.init();
  9. GeneticAlgorithm ga = gaEngineFactory.create(problem, listener→sseService.broadcast(jobId, …));
 10. AlgorithmResult result = ga.run(MAX_TRIALS);        // per generation: crossover → mutation → elite selection → track best → SSE event
 11. GenerateResponse resp = mapResult(result);          // inserted / conflicts / notInserted
 12. sseService.broadcast(jobId, ProgressEvent.completed(resp)); sseService.complete(jobId);
        │
        ▼
GET /api/sse/progress  ── client subscribed → receives { trial, generation, hardViolations, softPenalty, progress } … then completed
        │
        ▼
POST /api/timetable/save  { schedules:[inserted], notInserted:[ids] }
  → delete valid results (current semester, caller's jurusans) → insert Results (valid=1 placed / valid=0 notInserted)
  → SlotActivityService.revalidate()   (Laravel: Activity::validateSlots after save)
```

---

## 6. Entity → GA-model mapping (the corrected `ScheduleService` port)

| GA model field | Source (our JPA entity getter) | Note |
|----------------|-------------------------------|------|
| `AlgorithmActivity.id` | `Activity.getId()` | `Integer` |
| `.semesterId` | `Activity.getSemester().getId()` | was `getSemester_id()` |
| `.courseClass` | `Activity.getCourseClass()` | was `getCourse_class()` |
| `.courseSession` | `Activity.getCourseSession()` | was `getCourse_session()` |
| `.activityTypeId` | `Activity.getActivityType().getId()` | |
| `.quota` / `.duration` | `Activity.getQuota()` / `getDuration()` | |
| `.course` | `AlgorithmCourse(code,type,tingkat,konsentrasi)` from `Activity.getCourse()` | `type` = `CourseType` enum name (`Wajib`/`Pilihan`) |
| `.lecturerNiks` | `ActivityConstraintRepository.findByActivity_Id(id)` where `type==LECTURER` → `getValue()` | **new** — needed for `LecturerConflict` |
| `.slots` (`List<SlotsWithPriority>`) | `SlotActivityRepository.findByActivity_Id(id)` → `new SlotsWithPriority(sa.getSlot().getId(), sa.getPriority())` | the Phase-7 search space |
| `AlgorithmSlot.id` | `Slot.getId()` | |
| `.roomId` | `Slot.getRoom().getId()` | |
| `.time` | `AlgorithmTime(t.getId(), t.getDay(), t.getHour())` from `Slot.getTime()` | |
| `AlgorithmRoom.id` | `Room.getId()` | |
| `.roomCode` | `Room.getRoomCode()` | was `getRoom_code()` |
| `.roomTypeId` | `Room.getRoomType().getId()` | |
| `.capacity/.building/.floor` | `Room.getCapacity()/getBuilding()/getFloor()` | |
| `.parentId` | `Room.getParentRoom() != null ? getParentRoom().getId() : -1` | `-1` sentinel = "no parent" |

---

## 7. Code — genetic core (`schedule/algorithm/genetic/`)

### 7.1 `Gene.java` (verbatim port, `Integer` ids)

```java
package com.timetablingapp.schedule.algorithm.genetic;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Gene {
    private int activityIdx;   // index into GAContext.activities
    private int[] slotIds;     // chosen consecutive slot ids (may be empty)

    public Gene copy() {
        return new Gene(activityIdx, Arrays.copyOf(slotIds, slotIds.length));
    }

    @Override public String toString() {
        return "Activity " + activityIdx + " -> Slots " + Arrays.toString(slotIds);
    }
}
```

### 7.2 `FitnessVector.java` (verbatim — minimisation vector)

```java
package com.timetablingapp.schedule.algorithm.genetic.problem;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class FitnessVector implements Comparable<FitnessVector> {
    private int hardViolations;   // lower = better
    private int softPenalty;      // tiebreak, lower = better

    @Override public int compareTo(FitnessVector o) {
        if (hardViolations != o.hardViolations) return Integer.compare(hardViolations, o.hardViolations);
        return Integer.compare(softPenalty, o.softPenalty);
    }
    public FitnessVector copy() { return new FitnessVector(hardViolations, softPenalty); }
}
```

> **Convention (locked in):** *smaller* `FitnessVector` = *fitter*. Every comparison in the engine and in `Selection` uses natural ascending order. This is the single most error-prone point in the rewrite (see §3).

### 7.3 `Chromosome.java` (fixed `copy()` null-guard)

```java
package com.timetablingapp.schedule.algorithm.genetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessVector;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.model.AlgorithmActivity;
import com.timetablingapp.schedule.algorithm.model.SlotUsage;

import lombok.Data;

@Data
public class Chromosome implements Comparable<Chromosome> {
    private final List<Gene> gens;
    private final SlotUsage slotUsage;
    private final Problem problem;
    private FitnessVector fitness;

    public Chromosome(Problem problem) {
        this.gens = new ArrayList<>();
        this.slotUsage = new SlotUsage(problem);
        this.problem = problem;
        this.fitness = null;
    }

    public void addGens(Gene gene) {
        this.gens.add(gene);
        this.slotUsage.resolveSlotActivities(gene);
    }

    public Chromosome copy() {
        Chromosome c = new Chromosome(this.problem);
        this.gens.forEach(g -> c.addGens(g.copy()));
        c.fitness = (this.fitness != null) ? this.fitness.copy() : null;   // FIX: null-guard
        return c;
    }

    /** New child: genes 0..point from `first`, point..end from `second`. */
    public void crossOver(Chromosome first, Chromosome second, int point) {
        for (int i = 0; i < point; i++)                 this.addGens(first.gens.get(i).copy());
        for (int i = point; i < second.gens.size(); i++) this.addGens(second.gens.get(i).copy());
    }

    /** Re-roll one random gene's slots from its activity's still-free candidates. */
    public void changeRandomGeneSlot() {
        if (gens.isEmpty()) return;
        Gene gene = gens.get(new Random().nextInt(gens.size()));
        AlgorithmActivity act = problem.getContext().getActivityByIdx(gene.getActivityIdx());
        int[] free = act.getRandomFreeSlot();
        gene.setSlotIds(free != null ? free : new int[0]);
    }

    @Override public int compareTo(Chromosome o) { return this.fitness.compareTo(o.getFitness()); }
}
```

### 7.4 `Population.java` (verbatim)

```java
package com.timetablingapp.schedule.algorithm.genetic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.data.util.Pair;

public class Population {
    private final List<Chromosome> population = new ArrayList<>();
    private final int popSize;
    private final Random r = new Random();

    public Population(int popSize) { this.popSize = popSize; }

    public void add(Chromosome c) {
        if (population.size() < popSize) population.add(c);
        else throw new IllegalStateException("Population is full.");
    }
    public Chromosome selectOne() { return population.get(r.nextInt(population.size())).copy(); }
    public Pair<Chromosome, Chromosome> selectTwo() {
        return Pair.of(population.get(r.nextInt(population.size())).copy(),
                       population.get(r.nextInt(population.size())).copy());
    }
    public int getRemainingSlot() { return popSize - population.size(); }
    public List<Chromosome> getList() { return population; }
}
```

### 7.5 `operators/Crossover.java`, `Mutation.java`, `Selection.java`

`Crossover`/`Mutation` port verbatim (they only depend on the stateless `FitnessFunction` bean). `Selection.eliteSelection` sorts ascending (fittest first) — the rewrite's `Collections.sort(sorted)` is correct given our minimisation `compareTo`; keep it and drop the misleading "descending" comment.

```java
// operators/Selection.java
package com.timetablingapp.schedule.algorithm.genetic.operators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

import com.timetablingapp.schedule.algorithm.genetic.Chromosome;
import com.timetablingapp.schedule.algorithm.genetic.Population;

@Service
public class Selection {
    /** Elite = the `count` fittest (smallest FitnessVector). Returns deep copies. */
    public List<Chromosome> eliteSelection(Population population, int count) {
        List<Chromosome> sorted = new ArrayList<>(population.getList());
        Collections.sort(sorted);                       // ascending = fittest first
        List<Chromosome> elites = new ArrayList<>();
        for (int i = 0; i < Math.min(count, sorted.size()); i++) {
            elites.add(sorted.get(i).copy());
        }
        return elites;
    }
}
```

```java
// operators/Crossover.java  (verbatim, imports adjusted)
@Service
public class Crossover {
    private final FitnessFunction fitnessFunction;
    public Crossover(FitnessFunction fitnessFunction) { this.fitnessFunction = fitnessFunction; }

    public Pair<Chromosome, Chromosome> onePointCrossOver(Population population) {
        Pair<Chromosome, Chromosome> parents = population.selectTwo();
        Chromosome p1 = parents.getFirst(), p2 = parents.getSecond();
        int point = new Random().nextInt(Math.max(1, p1.getGens().size()));

        Chromosome c1 = new Chromosome(p1.getProblem()); c1.crossOver(p1, p2, point);
        c1.setFitness(fitnessFunction.calculate(c1));
        Chromosome c2 = new Chromosome(p1.getProblem()); c2.crossOver(p2, p1, point);
        c2.setFitness(fitnessFunction.calculate(c2));
        return Pair.of(c1, c2);
    }
}
```

```java
// operators/Mutation.java  (verbatim, imports adjusted)
@Service
public class Mutation {
    private final FitnessFunction fitnessFunction;
    public Mutation(FitnessFunction fitnessFunction) { this.fitnessFunction = fitnessFunction; }

    public Chromosome reselectSlotMutation(Population population, int mutationCount) {
        Chromosome c = population.selectOne();
        for (int i = 0; i < mutationCount; i++) c.changeRandomGeneSlot();
        c.setFitness(fitnessFunction.calculate(c));
        return c;
    }
}
```

### 7.6 `GeneticAlgorithm.java` (per-run; NPE + inverted-comparison fixes) and factory

```java
package com.timetablingapp.schedule.algorithm.genetic;

import java.util.List;
import org.springframework.data.util.Pair;

import com.timetablingapp.config.GAConfig;
import com.timetablingapp.schedule.algorithm.genetic.operators.Crossover;
import com.timetablingapp.schedule.algorithm.genetic.operators.Mutation;
import com.timetablingapp.schedule.algorithm.genetic.operators.Selection;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;

/** NOT a Spring bean — one instance per generate() run (holds mutable population/best). */
public class GeneticAlgorithm {
    private final Problem problem;
    private final FitnessFunction fitnessFunction;
    private final Crossover crossover;
    private final Mutation mutation;
    private final Selection selection;
    private final GAConfig cfg;
    private final GaProgressListener listener;

    private Population population;
    private Population newPopulation;
    public  Chromosome bestChromosome;

    public GeneticAlgorithm(Problem problem, FitnessFunction ff, Crossover c, Mutation m,
                            Selection s, GAConfig cfg, GaProgressListener listener) {
        this.problem = problem; this.fitnessFunction = ff; this.crossover = c;
        this.mutation = m; this.selection = s; this.cfg = cfg; this.listener = listener;
    }

    public AlgorithmResult run(int maxTrials) {
        int trial = 0;
        while (!problem.isSolved() && trial < maxTrials) {
            iteration(trial);
            trial++;
        }
        return problem.getResult();
    }

    private void iteration(int trial) {
        bestChromosome = null;
        initialPopulationGeneration();
        for (int gen = 0; gen < cfg.getGenerations(); gen++) {
            newPopulation = new Population(cfg.getPopulationSize());
            doCrossover();
            doMutation(2);
            doSelection();
            population = newPopulation;

            if (listener != null && bestChromosome != null) {
                FitnessVectorSnapshot best = FitnessVectorSnapshot.of(bestChromosome);
                double progress = (trial * cfg.getGenerations() + gen + 1.0)
                                / (double) (maxTrials * cfg.getGenerations());
                listener.onProgress(trial, gen, best.hard(), best.soft(), progress);
            }
        }
        problem.setSchedule(bestChromosome);   // materialise best into the Result
    }

    private void initialPopulationGeneration() {
        population = new Population(cfg.getPopulationSize());
        for (int i = 0; i < cfg.getPopulationSize(); i++) {
            Chromosome c = problem.createValidChromosome();
            c.setFitness(fitnessFunction.calculate(c));
            compareChromosomeFitness(c);
            population.add(c);
        }
    }

    /** FIX: null-guard first best; keep the SMALLER (fitter) vector. */
    private void compareChromosomeFitness(Chromosome c) {
        if (bestChromosome == null
                || c.getFitness().compareTo(bestChromosome.getFitness()) < 0) {
            bestChromosome = c.copy();
        }
    }

    private void doCrossover() {
        int target = (int) (cfg.getCrossoverRate() * cfg.getPopulationSize());
        for (int i = 0; i < target; i += 2) {
            Pair<Chromosome, Chromosome> pair = crossover.onePointCrossOver(population);
            addToNewPopulation(pair.getFirst());
            addToNewPopulation(pair.getSecond());
        }
    }
    private void doMutation(int m) {
        int target = (int) (cfg.getMutationRate() * cfg.getPopulationSize());
        for (int i = 0; i < target; i++) addToNewPopulation(mutation.reselectSlotMutation(population, m));
    }
    private void doSelection() {
        selection.eliteSelection(population, newPopulation.getRemainingSlot())
                 .forEach(this::addToNewPopulation);
    }
    private void addToNewPopulation(Chromosome c) {
        try { newPopulation.add(c); compareChromosomeFitness(c); }
        catch (IllegalStateException full) { /* population full → skip (matches legacy) */ }
    }

    // tiny helper to pull hard/soft out of the best chromosome for the SSE event
    private record FitnessVectorSnapshot(int hard, int soft) {
        static FitnessVectorSnapshot of(Chromosome c) {
            return new FitnessVectorSnapshot(c.getFitness().getHardViolations(),
                                             c.getFitness().getSoftPenalty());
        }
    }
}
```

```java
// GaProgressListener.java
package com.timetablingapp.schedule.algorithm.genetic;

@FunctionalInterface
public interface GaProgressListener {
    void onProgress(int trial, int generation, int hardViolations, int softPenalty, double progress);
}
```

```java
// GaEngineFactory.java  — assembles a per-run engine from singleton collaborators
package com.timetablingapp.schedule.algorithm.genetic;

import org.springframework.stereotype.Component;
import com.timetablingapp.config.GAConfig;
import com.timetablingapp.schedule.algorithm.genetic.operators.*;
import com.timetablingapp.schedule.algorithm.genetic.problem.FitnessFunction;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;

@Component
public class GaEngineFactory {
    private final FitnessFunction fitnessFunction;
    private final Crossover crossover;
    private final Mutation mutation;
    private final Selection selection;
    private final GAConfig cfg;

    public GaEngineFactory(FitnessFunction ff, Crossover c, Mutation m, Selection s, GAConfig cfg) {
        this.fitnessFunction = ff; this.crossover = c; this.mutation = m; this.selection = s; this.cfg = cfg;
    }

    public GeneticAlgorithm create(Problem problem, GaProgressListener listener) {
        return new GeneticAlgorithm(problem, fitnessFunction, crossover, mutation, selection, cfg, listener);
    }
}
```

---

## 8. Code — problem, constraints, models

### 8.1 `problem/FitnessFunction.java` + `FitnessFunctionFactory.java`

Port verbatim (they are stateless singletons). `FitnessFunction.calculate` sums `hardViolations` for hard constraints and `softPenalty` for soft, returning a `FitnessVector`. `FitnessFunctionFactory` is optional (used only if you later want per-run constraint sets); keep it for parity.

```java
@Service
public class FitnessFunction {
    private final List<Constraint> constraints;   // all Constraint beans injected
    public FitnessFunction(List<Constraint> constraints) { this.constraints = constraints; }

    public FitnessVector calculate(Chromosome chromosome) {
        int hard = 0, soft = 0;
        for (Constraint c : constraints) {
            ConstraintResult r = c.evaluate(chromosome);
            if (c.isHard()) hard += r.getHardViolations();
            else            soft += r.getSoftPenalty();
        }
        return new FitnessVector(hard, soft);
    }
}
```

### 8.2 `problem/Problem.java` (drop `@Service`/`@PostConstruct`, explicit `init()`)

Port `Problem` almost verbatim but: (a) no Spring annotations; (b) `init()` becomes public, called by `TimetableService`; (c) all `Long` → `Integer`; (d) keep `setSchedule`, `createValidChromosome`, `isSolved`, `getResult` unchanged in logic. The `removeFreeSlotByActivity` cross-activity pruning stays a documented `// TODO` (it was a no-op in the source — the hard constraints handle those conflicts at fitness time instead).

### 8.3 Constraint interface + result (verbatim)

```java
public interface Constraint {
    boolean isHard();
    int getWeight();
    String getName();
    ConstraintResult evaluate(Chromosome chromosome);
}

@Data @AllArgsConstructor
public class ConstraintResult {
    private int hardViolations;
    private int softPenalty;
    private Set<Integer> conflictedActs;
}
```

### 8.4 `ConflictedSlotsConstraint` (verbatim) — hard, weight 1000

Double-booking of a physical slot: group `SlotUsage.getSlotActs()` (`slotId → [activityIdx]`); any slot with >1 activity marks all those activities conflicted. `hardViolations = conflicted.size() * 1000`.

### 8.5 `ConflictedActivityConstraint` (verbatim structure) — hard, weight 1000

Group `SlotUsage.getDayHourActs()` (`"day_hour" → [activityIdx]`); for each pair in the same day+hour, if **any** `ActivityPairConstraint.isConflict(a1,a2,ctx)` holds, both are conflicted. This is where the three implemented pair-constraints below plug in via DI (`List<ActivityPairConstraint>`).

### 8.6 The three pair-constraints — **now implemented** (§3 fix)

Ported from the commented-out `Activity::isSameLecturer / isSameCourseClass / isCourseConflict`:

```java
// activity/ActivityPairConstraint.java
public interface ActivityPairConstraint {
    boolean isConflict(int a1, int a2, GAContext ctx);
}
```

```java
// activity/LecturerConflict.java  — same lecturer teaching two activities at once
@Component
public class LecturerConflict implements ActivityPairConstraint {
    @Override public boolean isConflict(int a1, int a2, GAContext ctx) {
        Set<String> l1 = ctx.getActivityByIdx(a1).getLecturerNiks();
        Set<String> l2 = ctx.getActivityByIdx(a2).getLecturerNiks();
        for (String nik : l1) if (l2.contains(nik)) return true;
        return false;
    }
}
```

```java
// activity/CourseClassConflict.java — same course code + same class (different session) clashing
@Component
public class CourseClassConflict implements ActivityPairConstraint {
    @Override public boolean isConflict(int a1, int a2, GAContext ctx) {
        AlgorithmActivity x = ctx.getActivityByIdx(a1), y = ctx.getActivityByIdx(a2);
        return x.getCourse().getCode().equals(y.getCourse().getCode())
            && x.getCourseClass().equals(y.getCourseClass());
    }
}
```

```java
// activity/CourseConflict.java — curriculum "bentrok" (same tingkat/konsentrasi grouping)
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
```

> These are exactly the semantics Phase 7's `SlotValidationService` already encodes as `bentrokWajib`/`bentrokPilihan`/`courseTaken` (see [phase7 §6](phase7.md)). Keeping them identical guarantees the GA never *un-does* a placement the validator considered legal.

### 8.7 `LecturerMovingConstraint` / `RoomIdleConstraint` — no-op soft (§3 fix)

```java
@Component
public class RoomIdleConstraint implements Constraint {   // (same shape for LecturerMovingConstraint)
    @Override public boolean isHard() { return false; }
    @Override public int getWeight() { return 0; }
    @Override public String getName() { return "room idle (TODO Phase 8+)"; }
    @Override public ConstraintResult evaluate(Chromosome c) { return new ConstraintResult(0, 0, Set.of()); }
}
```

### 8.8 Models — `SlotUsage.java` (all three §3 bugs fixed)

```java
package com.timetablingapp.schedule.algorithm.model;

import java.util.*;
import java.util.stream.Collectors;

import com.timetablingapp.schedule.algorithm.genetic.Gene;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;

public class SlotUsage {
    private final List<SlotActivityUsage> slotActivities = new ArrayList<>();   // FIX: init
    private final Problem problem;

    public SlotUsage(Problem problem) { this.problem = problem; }

    public void resolveSlotActivities(Gene gene) {
        int activityIdx = gene.getActivityIdx();
        int[] slotIds = gene.getSlotIds();                                       // FIX: int[]
        for (int i = 0; i < slotIds.length; i++) {                               // FIX: < not <=
            AlgorithmSlot s = problem.getContext().getSlotById(slotIds[i]);
            addSlotActivity(s.getId(), activityIdx);
            if (s.getParent() != null) addSlotActivity(s.getParent().getId(), activityIdx);
            for (AlgorithmSlot child : s.getChilds()) addSlotActivity(child.getId(), activityIdx);
        }
    }

    private void addSlotActivity(int slotId, int activityIdx) {
        AlgorithmSlot slot = problem.getContext().getSlotById(slotId);
        slotActivities.add(new SlotActivityUsage(
                slotId, slot.getRoomId(), slot.getTime().getDay(), slot.getTime().getHour(), activityIdx));
    }

    public Map<Integer, List<Integer>> getSlotActs() {   // slotId -> [activityIdx]
        return slotActivities.stream().collect(Collectors.groupingBy(
                SlotActivityUsage::getSlotId,
                Collectors.mapping(SlotActivityUsage::getActivityIdx, Collectors.toList())));
    }
    public Map<Integer, List<Integer>> getActSlots() {   // activityIdx -> [slotId]
        return slotActivities.stream().collect(Collectors.groupingBy(
                SlotActivityUsage::getActivityIdx,
                Collectors.mapping(SlotActivityUsage::getSlotId, Collectors.toList())));
    }
    public Map<String, List<Integer>> getDayHourActs() { // "day_hour" -> [activityIdx]
        return slotActivities.stream().collect(Collectors.groupingBy(
                sa -> sa.getDay() + "_" + sa.getHour(),
                Collectors.mapping(SlotActivityUsage::getActivityIdx, Collectors.toList())));
    }
}
```

> **Note on the original `addSlotActivities` "dedup":** the rewrite skipped a `(slotId)` if *already present with any activity*, which silently dropped genuine double-bookings and defeated `ConflictedSlotsConstraint`. The fix above records every `(slot, activity)` pair so grouping actually reveals conflicts. Keep the `SlotActs`/`ActSlots` grouping semantics identical to `Problem.setSchedule`'s expectations.

### 8.9 `GAContext.java` (maps instantiated; parent/child slot linking)

```java
package com.timetablingapp.schedule.algorithm.model;

import java.util.*;
import lombok.Getter;

@Getter
public class GAContext {
    private final List<AlgorithmActivity> activities;
    private final List<AlgorithmSlot> slots;
    private final List<AlgorithmRoom> rooms;
    private final List<com.timetablingapp.schedule.algorithm.io.Schedule> initialSchedules;

    private final Map<Integer, Integer> activityIndexById = new HashMap<>();   // FIX: init all three
    private final Map<Integer, Integer> slotIndexById     = new HashMap<>();
    private final Map<Integer, Integer> roomIndexById     = new HashMap<>();

    public GAContext(List<AlgorithmActivity> activities, List<AlgorithmSlot> slots,
                     List<AlgorithmRoom> rooms,
                     List<com.timetablingapp.schedule.algorithm.io.Schedule> initialSchedules) {
        this.activities = activities; this.slots = slots; this.rooms = rooms;
        this.initialSchedules = (initialSchedules != null) ? initialSchedules : List.of();
    }

    public void init() {
        for (int i = 0; i < rooms.size(); i++)    roomIndexById.put(rooms.get(i).getId(), i);
        for (int i = 0; i < slots.size(); i++)    slotIndexById.put(slots.get(i).getId(), i);
        for (int i = 0; i < activities.size(); i++) {
            activityIndexById.put(activities.get(i).getId(), i);
            activities.get(i).resolveSlots(this);
        }
        slots.forEach(s -> { s.resolveRoom(this); });
        slots.forEach(s -> s.resolveParentChild(this));   // second pass: parent/child need all rooms resolved
        initialSchedules.forEach(sch -> sch.init(this));
    }

    public int getActivityIndexById(Integer id) { return activityIndexById.get(id); }
    public AlgorithmActivity getActivityByIdx(int i) { return activities.get(i); }
    public AlgorithmSlot getSlotById(int id) { return slots.get(slotIndexById.get(id)); }
    public AlgorithmRoom getRoomById(Integer id) { return rooms.get(roomIndexById.get(id)); }

    /** Consecutive slots [startId .. startId+duration-1] — relies on seeded contiguous ids (A1). */
    public int[] getSlotStartToDuration(int startId, int duration) {
        int[] ids = new int[duration];
        for (int i = 0; i < duration; i++) ids[i] = startId + i;
        return ids;
    }

    public List<Integer> getSlotIdsByDayTime(Integer roomId, int day, int startH, int endH) {
        List<Integer> out = new ArrayList<>();
        for (AlgorithmSlot s : slots) {
            AlgorithmTime t = s.getTime();
            if (Objects.equals(s.getRoomId(), roomId) && t.getDay() == day
                    && t.getHour() >= startH && t.getHour() < endH) out.add(s.getId());
        }
        return out;
    }
}
```

> **`getSlotStartToDuration` change vs source:** the rewrite fetched `getSlotById(startId+i)`; we return raw ids (avoids a map lookup and NPE if a computed id is outside the filtered slot list). `SlotsWithPriority` stores the ids; `SlotUsage` resolves each via `getSlotById` where existence is guaranteed. If a filtered run can produce a start slot whose `+duration` neighbours were filtered out, guard in `SlotsWithPriority.resolveSlots` and skip the candidate (documented TODO).

### 8.10 Remaining models (brief)

- **`AlgorithmTime`** = `{Integer id, int day, int hour}` (verbatim `Time`).
- **`AlgorithmRoom`** = `{Integer id, String roomCode, Integer roomTypeId, int capacity, String building, String floor, Integer parentId}` (verbatim `Room`; `parentId=-1` = none).
- **`AlgorithmCourse`** = `{String code, String type, int tingkat, String konsentrasi}` (`type` holds `"Wajib"`/`"Pilihan"`).
- **`AlgorithmActivity`** = verbatim `Activity` **plus** `Set<String> lecturerNiks` and `Integer`-typed ids; keep `resolveSlots`, `getRandomFreeSlot` (returns `int[]`), `slotsIsEmpty`, `removeSlotIfSlotExist`.
- **`AlgorithmSlot`** = verbatim `Slot` with `Integer` ids; `resolveRoom` / `resolveParentChild` link parent & child slots at the same `time_id` via `GAContext` (mirrors `createJSONRequest2`'s `slotsMap[room][time]`).
- **`SlotsWithPriority`** = verbatim `SlotsWPriority` (`int[] slots`, `int slotStartId`, `int priority`, `resolveSlots(ctx, duration)`, `getSlotIds()`).
- **`SlotActivityUsage`** = verbatim `SlotActivity(evaluate)` renamed: `{int slotId, int roomId, int day, int hour, int activityIdx}`.

### 8.11 `io/AlgorithmResult.java` + `io/Schedule.java` (verbatim, `Integer` ids)

`AlgorithmResult` = `{List<Schedule> schedule, Set<Integer> conflictedActIds, Set<Integer> notInsertedActIds}` with `addSchedule/isScheduled/isSlotOccupied/addConflictedActivities/addNotInsertedActivities/initSchedule`.
`Schedule` = `{Integer activityId, Integer roomId, int day, int startTime, int endTime, List<Integer> slotIds}`; `init(ctx)` resolves `slotIds` from `roomId/day/start/end` (used for the locked/initial schedules).

---

## 9. `TimetableService` — mapping + orchestration + async

```java
package com.timetablingapp.schedule.algorithm;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.timetablingapp.activity.*;
import com.timetablingapp.activity.constraint.*;
import com.timetablingapp.result.*;
import com.timetablingapp.room.*;
import com.timetablingapp.schedule.algorithm.dto.*;
import com.timetablingapp.schedule.algorithm.genetic.*;
import com.timetablingapp.schedule.algorithm.genetic.problem.Problem;
import com.timetablingapp.schedule.algorithm.io.AlgorithmResult;
import com.timetablingapp.schedule.algorithm.io.Schedule;
import com.timetablingapp.schedule.algorithm.model.*;
import com.timetablingapp.schedule.slot.*;
import com.timetablingapp.schedule.slot.act.*;
import com.timetablingapp.semester.*;
import com.timetablingapp.setting.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private static final int MAX_TRIALS = 10;   // matches ScheduleService.run(10)

    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository activityConstraintRepository;
    private final SlotRepository slotRepository;
    private final SlotActivityRepository slotActivityRepository;
    private final RoomRepository roomRepository;
    private final ResultRepository resultRepository;
    private final SemesterRepository semesterRepository;
    private final SettingService settingService;
    private final GaEngineFactory gaEngineFactory;
    private final SlotActivityService slotActivityService;   // to revalidate on save
    private final SseService sseService;

    private final AtomicLong jobSeq = new AtomicLong();

    // ---- generate -----------------------------------------------------------

    /** Async entry: returns jobId immediately; progress + result stream over SSE. */
    public String startGenerate(GenerateRequest req) {
        String jobId = "gen-" + jobSeq.incrementAndGet();
        runGenerate(jobId, req);          // @Async — returns instantly
        return jobId;
    }

    @Async("gaExecutor")
    public void runGenerate(String jobId, GenerateRequest req) {
        try {
            GenerateResponse resp = generateSync(req, (trial, gen, hard, soft, progress) ->
                    sseService.broadcast(jobId, ProgressEvent.running(jobId, trial, gen, hard, soft, progress)));
            sseService.broadcast(jobId, ProgressEvent.completed(jobId, resp));
        } catch (Exception e) {
            sseService.broadcast(jobId, ProgressEvent.error(jobId, e.getMessage()));
        } finally {
            sseService.complete(jobId);
        }
    }

    /** Synchronous core — also usable directly via ?wait=true and by tests. */
    @Transactional(readOnly = true)
    public GenerateResponse generateSync(GenerateRequest req, GaProgressListener listener) {
        Integer semesterId = currentSemester().getId();
        SettingDetailResponse setting = (req.getSettingId() != null)
                ? settingService.findDetail(req.getSettingId()) : null;

        List<AlgorithmActivity> activities = loadActivities(semesterId, setting);
        List<AlgorithmSlot> slots = slotRepository.findAllBy().stream().map(this::toSlot).toList();
        List<AlgorithmRoom> rooms = roomRepository.findAll().stream().map(this::toRoom).toList();
        List<Schedule> locked = mapLocked(req.getLockedSchedules());

        GAContext ctx = new GAContext(activities, slots, rooms, locked);
        ctx.init();
        Problem problem = new Problem(ctx);
        problem.init();

        GeneticAlgorithm ga = gaEngineFactory.create(problem, listener);
        AlgorithmResult result = ga.run(MAX_TRIALS);
        return mapResult(result, ctx);
    }

    // ---- mapping: entities → GA models (the corrected ScheduleService) -------

    private List<AlgorithmActivity> loadActivities(Integer semesterId, SettingDetailResponse setting) {
        List<Activity> entities = activityRepository.findAllForScheduling(semesterId);
        // setting filters (empty setting → all)
        Set<Integer> allowActIds  = idSet(setting, "activity");       // CUSTOM_ACTIVITY
        Set<Integer> allowType    = idSet(setting, "activityType");   // ACTIVITY_TYPE
        Set<Integer> allowJurusan = idSet(setting, "jurusan");        // JURUSAN

        List<AlgorithmActivity> out = new ArrayList<>();
        for (Activity a : entities) {
            if (setting != null) {
                if (!allowActIds.isEmpty() && !allowActIds.contains(a.getId())) continue;
                if (!allowType.isEmpty() && !allowType.contains(a.getActivityType().getId())) continue;
                if (!allowJurusan.isEmpty() && a.getCourse().getJurusan() != null
                        && !allowJurusan.contains(a.getCourse().getJurusan().getId())) continue;
            }
            out.add(toActivity(a, setting));
        }
        return out;
    }

    private AlgorithmActivity toActivity(Activity a, SettingDetailResponse setting) {
        var c = a.getCourse();
        AlgorithmCourse course = new AlgorithmCourse(
                c.getCode(), c.getType() != null ? c.getType().name() : null,
                c.getTingkat() != null ? c.getTingkat() : 0, c.getKonsentrasi());

        Set<String> niks = new HashSet<>();
        for (ActivityConstraint ac : activityConstraintRepository.findByActivity_Id(a.getId())) {
            if (ac.getType() == ConstraintType.LECTURER) niks.add(ac.getValue());
        }

        List<SlotsWithPriority> slots = new ArrayList<>();
        for (SlotActivity sa : slotActivityRepository.findByActivity_Id(a.getId())) {
            if (passesSlotFilter(sa.getSlot(), setting)) {
                slots.add(new SlotsWithPriority(sa.getSlot().getId(), sa.getPriority()));
            }
        }
        return new AlgorithmActivity(a.getId(), a.getSemester().getId(), course,
                a.getCourseClass(), a.getCourseSession(), a.getActivityType().getId(),
                a.getQuota(), a.getDuration(), slots, niks);
    }

    /** Setting slot filter: ROOM_TYPE, ROOM_OWNER, WAKTU (hour), HARI (day). */
    private boolean passesSlotFilter(Slot slot, SettingDetailResponse setting) {
        if (setting == null) return true;
        var room = slot.getRoom(); var time = slot.getTime();
        Set<Integer> types = idSet(setting, "roomType");
        Set<Integer> owners = idSet(setting, "room");
        Set<Integer> hours = idSet(setting, "waktu");
        Set<Integer> days  = idSet(setting, "hari");
        if (!types.isEmpty() && !types.contains(room.getRoomType().getId())) return false;
        if (!owners.isEmpty() && !owners.contains(room.getId())) return false;
        if (!hours.isEmpty() && !hours.contains(time.getHour())) return false;
        if (!days.isEmpty()  && !days.contains(time.getDay())) return false;
        return true;
    }

    private AlgorithmSlot toSlot(Slot s) {
        var t = s.getTime();
        return new AlgorithmSlot(s.getId(), s.getRoom().getId(),
                new AlgorithmTime(t.getId(), t.getDay(), t.getHour()));
    }
    private AlgorithmRoom toRoom(Room r) {
        return new AlgorithmRoom(r.getId(), r.getRoomCode(), r.getRoomType().getId(),
                r.getCapacity(), r.getBuilding(), r.getFloor(),
                r.getParentRoom() != null ? r.getParentRoom().getId() : -1);
    }
    private List<Schedule> mapLocked(List<ScheduleDto> locked) {
        if (locked == null) return List.of();
        List<Schedule> out = new ArrayList<>();
        for (ScheduleDto d : locked) {
            Schedule s = new Schedule();
            s.setActivityId(d.getActivityId()); s.setRoomId(d.getRoomId());
            s.setDay(d.getDay()); s.setStartTime(d.getStartTime()); s.setEndTime(d.getEndTime());
            out.add(s);
        }
        return out;
    }

    // ---- mapping: GA Result → frontend response (port of getAlgorithm tail) --

    private GenerateResponse mapResult(AlgorithmResult result, GAContext ctx) {
        List<ScheduleDto> inserted = new ArrayList<>();
        for (Schedule sch : result.getSchedule()) {
            if (sch.getSlotIds() == null || sch.getSlotIds().isEmpty()) continue;  // skip locked-only
            AlgorithmSlot first = ctx.getSlotById(sch.getSlotIds().get(0));
            AlgorithmActivity act = ctx.getActivityByIdx(ctx.getActivityIndexById(sch.getActivityId()));
            inserted.add(new ScheduleDto(act.getId(), first.getRoomId(),
                    first.getTime().getDay(), first.getTime().getHour(),
                    first.getTime().getHour() + act.getDuration()));
        }
        List<Integer> conflicts = new ArrayList<>(result.getConflictedActIds());
        List<Integer> notInserted = new ArrayList<>(result.getConflictedActIds());
        notInserted.addAll(result.getNotInsertedActIds());
        Collections.sort(conflicts); Collections.sort(notInserted);
        return GenerateResponse.result(inserted, conflicts, notInserted);
    }

    // ---- save (port of TableController::saveData) ---------------------------

    @Transactional
    public void save(SaveTimetableRequest req) {
        Integer semesterId = currentSemester().getId();
        resultRepository.deleteBySemester_Id(semesterId);       // clear current semester's results

        for (Integer actId : req.getNotInserted()) {
            Result r = new Result();
            r.setSemester(semesterRepository.getReferenceById(semesterId));
            r.setActivity(activityRepository.getReferenceById(actId));
            r.setValid(false);
            resultRepository.save(r);
        }
        for (ScheduleDto d : req.getSchedules()) {
            Result r = new Result();
            r.setSemester(semesterRepository.getReferenceById(semesterId));
            r.setActivity(activityRepository.getReferenceById(d.getActivityId()));
            r.setRoom(roomRepository.getReferenceById(d.getRoomId()));
            r.setDay(String.valueOf(d.getDay()));
            r.setStartTime(LocalTime.of(d.getStartTime(), 0));  // Laravel Carbon->hour(start_time)
            r.setEndTime(LocalTime.of(d.getEndTime(), 0));
            r.setValid(true);
            resultRepository.save(r);
        }
        slotActivityService.revalidate();   // Laravel: Activity::validateSlots(...) after save
    }

    // ---- read: prepareScheduleData / getData --------------------------------

    @Transactional(readOnly = true)
    public ScheduleDataResponse prepareScheduleData(Integer semesterId) {
        List<Result> results = resultRepository.findBySemester_Id(semesterId);
        List<ScheduleDto> inserted = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();
        for (Result r : results) {
            if (!Boolean.TRUE.equals(r.getValid()) || r.getRoom() == null || r.getDay() == null) continue;
            inserted.add(new ScheduleDto(r.getActivity().getId(), r.getRoom().getId(),
                    Integer.parseInt(r.getDay()), r.getStartTime().getHour(), r.getEndTime().getHour()));
            placed.add(r.getActivity().getId());
        }
        List<Integer> notInserted = activityRepository.findBySemester_Id(semesterId).stream()
                .map(Activity::getId).filter(id -> !placed.contains(id)).toList();
        return new ScheduleDataResponse(inserted, notInserted);
    }

    // ---- helpers ------------------------------------------------------------

    private Set<Integer> idSet(SettingDetailResponse setting, String key) {
        if (setting == null || setting.getConstraints() == null) return Set.of();
        List<String> vals = setting.getConstraints().getOrDefault(key, List.of());
        Set<Integer> out = new HashSet<>();
        for (String v : vals) try { out.add(Integer.valueOf(v)); } catch (NumberFormatException ignored) {}
        return out;
    }
    private Semester currentSemester() {
        return semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new com.timetablingapp.common.exception.BadRequestException("No current semester is set"));
    }
}
```

> **Faculty scoping (`Jurusan::jurusanIds()`):** the Laravel queries constantly filter by the caller's faculty jurusans. If Phase 2/3 already introduced a `JurusanService.currentUserJurusanIds()` helper, apply it in `loadActivities` / `prepareScheduleData` / `save` exactly where Laravel calls `Jurusan::jurusanIds()`. If not yet available, add a `// TODO faculty-scope` and default to all — but do wire it before Phase 10.

---

## 10. SSE — controller + service (replaces `SocketController`)

### 10.1 `SseService.java`

```java
package com.timetablingapp.schedule.algorithm;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.timetablingapp.schedule.algorithm.dto.ProgressEvent;

@Service
public class SseService {

    private static final long TIMEOUT = 30 * 60 * 1000L;   // 30 min
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** Register a client for a jobId (or a shared channel if you broadcast to all). */
    public SseEmitter register(String jobId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(()   -> emitters.remove(jobId));
        emitter.onError(e      -> emitters.remove(jobId));
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("jobId", jobId))); }
        catch (IOException ignored) {}
        return emitter;
    }

    public void broadcast(String jobId, ProgressEvent event) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;
        try { emitter.send(SseEmitter.event().name(event.getStatus()).data(event)); }
        catch (IOException e) { emitters.remove(jobId); }
    }

    public void complete(String jobId) {
        SseEmitter emitter = emitters.remove(jobId);
        if (emitter != null) emitter.complete();
    }
}
```

### 10.2 `SseController.java`

```java
package com.timetablingapp.schedule.algorithm;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    /** GET /api/sse/progress?jobId=gen-42 — stream GA progress for a run. */
    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public SseEmitter progress(@RequestParam String jobId) {
        return sseService.register(jobId);
    }
}
```

> **Ordering:** the client should `POST /generate` → get `jobId` → open `GET /sse/progress?jobId=…`. Because `runGenerate` is `@Async`, there is a small window where the first events could fire before the client subscribes. `SseService.register` immediately sends a `connected` event; the GA's first `running` event is emitted after `initialPopulationGeneration` (tens of ms), which in practice lands after subscription. If flakiness appears in tests, buffer the last event per job and replay it on `register` (documented TODO).

### 10.3 `ProgressEvent.java`

```java
package com.timetablingapp.schedule.algorithm.dto;

import lombok.*;

@Getter @Builder @AllArgsConstructor
public class ProgressEvent {
    private String status;            // "running" | "completed" | "error"
    private String jobId;
    private Integer trial;
    private Integer generation;
    private Integer hardViolations;
    private Integer softPenalty;
    private Double  progress;         // 0.0 .. 1.0
    private GenerateResponse result;  // set only on "completed"
    private String message;           // set only on "error"

    public static ProgressEvent running(String jobId, int trial, int gen, int hard, int soft, double p) {
        return ProgressEvent.builder().status("running").jobId(jobId)
                .trial(trial).generation(gen).hardViolations(hard).softPenalty(soft).progress(p).build();
    }
    public static ProgressEvent completed(String jobId, GenerateResponse r) {
        return ProgressEvent.builder().status("completed").jobId(jobId).progress(1.0).result(r).build();
    }
    public static ProgressEvent error(String jobId, String msg) {
        return ProgressEvent.builder().status("error").jobId(jobId).message(msg).build();
    }
}
```

---

## 11. `TimetableController.java` + DTOs + endpoint table

```java
package com.timetablingapp.schedule.algorithm;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.timetablingapp.common.dto.MessageResponse;
import com.timetablingapp.schedule.algorithm.dto.*;
import com.timetablingapp.semester.SemesterRepository;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final SemesterRepository semesterRepository;

    /** POST /api/timetable/generate — kick off GA. Returns { jobId }. Add ?wait=true for sync. */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest req,
                                                     @RequestParam(defaultValue = "false") boolean wait) {
        if (wait) return ResponseEntity.ok(timetableService.generateSync(req, null));
        return ResponseEntity.ok(GenerateResponse.job(timetableService.startGenerate(req)));
    }

    /** POST /api/timetable/save — persist inserted + notInserted, then revalidate. */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> save(@RequestBody SaveTimetableRequest req) {
        timetableService.save(req);
        return ResponseEntity.ok(MessageResponse.success("Timetable saved"));
    }

    /** GET /api/timetable/init-schedule — current semester's placed/unplaced (ADMIN). */
    @GetMapping("/init-schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScheduleDataResponse> initSchedule() {
        Integer sid = semesterRepository.findByCurrentTrue().orElseThrow().getId();
        return ResponseEntity.ok(timetableService.prepareScheduleData(sid));
    }

    /** GET /api/timetable/schedule/{semesterId} — view any semester's schedule (auth). */
    @GetMapping("/schedule/{semesterId}")
    public ResponseEntity<ScheduleDataResponse> schedule(@PathVariable Integer semesterId) {
        return ResponseEntity.ok(timetableService.prepareScheduleData(semesterId));
    }

    /** GET /api/timetable/data — activities+rooms maps for the display grid (auth). */
    @GetMapping("/data")
    public ResponseEntity<TimetableDataResponse> data() {
        return ResponseEntity.ok(timetableService.getData());
    }
}
```

### 11.1 DTOs (record-style)

```java
// dto/GenerateRequest.java
@Data public class GenerateRequest {
    private Integer settingId;                 // nullable → schedule everything
    private List<ScheduleDto> lockedSchedules; // already-placed activities the GA must respect
}

// dto/ScheduleDto.java  — one placed activity (int hours, matching Laravel)
@Data @AllArgsConstructor @NoArgsConstructor
public class ScheduleDto {
    private Integer activityId;
    private Integer roomId;
    private Integer day;        // 1..7
    private Integer startTime;  // hour (7..23)
    private Integer endTime;    // hour
}

// dto/GenerateResponse.java  — async job OR sync result
@Getter @Builder @AllArgsConstructor
public class GenerateResponse {
    private String jobId;                  // async
    private List<ScheduleDto> inserted;    // sync
    private List<Integer> conflicts;
    private List<Integer> notInserted;
    public static GenerateResponse job(String id) { return GenerateResponse.builder().jobId(id).build(); }
    public static GenerateResponse result(List<ScheduleDto> ins, List<Integer> c, List<Integer> n) {
        return GenerateResponse.builder().inserted(ins).conflicts(c).notInserted(n).build();
    }
}

// dto/SaveTimetableRequest.java
@Data public class SaveTimetableRequest {
    private List<ScheduleDto> schedules;   // placed (valid=1)
    private List<Integer> notInserted;     // unplaced (valid=0)
}

// dto/ScheduleDataResponse.java
@Data @AllArgsConstructor public class ScheduleDataResponse {
    private List<ScheduleDto> inserted;
    private List<Integer> notInserted;
}

// dto/TimetableDataResponse.java — mirrors getData()'s { activities:{id→…}, rooms:{id→…} }
@Data @AllArgsConstructor public class TimetableDataResponse {
    private Map<Integer, ActivityView> activities;
    private Map<Integer, RoomView> rooms;
    // ActivityView: activity + resolved lecturers/rooms/roomTypes + color + name
    // RoomView: room + childs (List<Integer>) + roomType + availability windows
}
```

> `getData()` in `TimetableService` is a straight aggregation port of `TableController::getData` (lines 73–132): build `rooms` map with `childs` back-references, then `activities` map with each activity's resolved lecturers (`ActivityConstraint` LECTURER niks → `Lecturer`), rooms (ROOM ids → `Room`), and room types (ROOM_TYPE ids → `RoomType`). Reuse the existing `ActivityResponse`/`RoomResponse` DTOs where possible instead of exposing entities. This method is display-only and has no algorithm logic — implement it last.

### 11.2 Endpoint summary

| Endpoint | Method | Auth | Laravel origin | Returns |
|----------|--------|------|----------------|---------|
| `/api/timetable/generate` | POST | ADMIN | `getAlgorithm` | `{ jobId }` (async) or `{ inserted, conflicts, notInserted }` (`?wait=true`) |
| `/api/timetable/save` | POST | ADMIN | `saveData` | `MessageResponse` |
| `/api/timetable/init-schedule` | GET | ADMIN | `getInitSchedule` | `ScheduleDataResponse` |
| `/api/timetable/schedule/{semesterId}` | GET | auth | `getSchedule`/`showSemesterData` | `ScheduleDataResponse` |
| `/api/timetable/data` | GET | auth | `getData` | `TimetableDataResponse` |
| `/api/sse/progress?jobId=` | GET | ADMIN | `SocketController@sse` | `text/event-stream` |

> **Dropped:** Laravel's `show()` (returns a Blade `timetable.index` view with the settings list + vlock flag) and `getSchedule($id)` (returns `timetable.show` view) are **view** routes. In a REST backend the frontend renders the page; the *data* it needs is already covered — settings come from Phase 6's `GET /api/settings`, the stale flag from Phase 7's `GET /api/slot-activities/status`, and schedule data from `/schedule/{id}`. No new endpoint required. `getAlgorithm2`/`createJSONRequest`/`dummyResult`/`dummyActSlots` were dev-only scaffolding — **not ported**.

---

## 12. Config edits

### 12.1 `config/AsyncConfig.java` (ADD)

```java
package com.timetablingapp.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("gaExecutor")
    public Executor gaExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);          // GA is CPU-heavy; keep concurrency low
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(10);
        ex.setThreadNamePrefix("ga-");
        ex.initialize();
        return ex;
    }
}
```

### 12.2 `config/SecurityConfig.java` (EDIT)

- `POST /api/timetable/generate`, `/save`, `GET /api/timetable/init-schedule`, `GET /api/sse/**` → `hasRole('ADMIN')`.
- `GET /api/timetable/schedule/**`, `GET /api/timetable/data` → `authenticated()`.
- If the frontend uses the browser `EventSource` API (which cannot set an `Authorization` header — see R3), either (a) accept the JWT as `?token=` on `/api/sse/**` and resolve it in `JwtAuthenticationFilter`, or (b) rely on an already-authenticated session cookie. Prefer (a) with a narrowly-scoped query-param read only for `/api/sse/**`.

### 12.3 `application.properties` (verify Phase 1 defaults)

`ga.population-size`, `ga.generations`, `ga.crossover-rate`, `ga.mutation-rate` already exist (`GAConfig`). For first bring-up use small values (`generations=50`, `population-size=30`) to keep runs fast, then tune.

---

## 13. Repository / entity EDITs (exact additions)

```java
// schedule/slot/act/SlotActivityRepository.java  (+)
List<SlotActivity> findByActivity_Id(Integer activityId);

// activity/constraint/ActivityConstraintRepository.java  (+)
List<ActivityConstraint> findByActivity_Id(Integer activityId);

// activity/ActivityRepository.java  (+)  — avoid N+1 in the mapping loop
@Query("select distinct a from Activity a " +
       "join fetch a.course c join fetch a.activityType " +
       "where a.semester.id = :semesterId")
List<Activity> findAllForScheduling(@Param("semesterId") Integer semesterId);
```

No entity schema changes — `slot_acts`, `activities`, `results`, `slots`, `rooms`, `activity_constraints` are all already mapped and pass `ddl-auto=validate` from Phases 3–7.

---

## 14. Verification criteria

- [ ] `./gradlew clean build` compiles — all GA package files present, none of the §3 compile bugs remain (`SlotUsage` `long[]`/off-by-one, `GAContext` maps).
- [ ] Spring context starts: `FitnessFunction` receives a non-empty `List<Constraint>`; `ConflictedActivityConstraint` receives a non-empty `List<ActivityPairConstraint>` (the 3 implemented beans).
- [ ] `POST /api/timetable/generate?wait=true` with a seeded semester returns `{ inserted, conflicts, notInserted }` and completes without NPE.
- [ ] **No lecturer conflicts**: no two `inserted` activities sharing a NIK overlap in day+hour (verified by re-running `LecturerConflict` over the response).
- [ ] **No room double-booking**: no two `inserted` activities share a `(room, day, hour)` (i.e. `ConflictedSlotsConstraint` = 0 on the best chromosome).
- [ ] **No curriculum overlap (bentrok)**: `CourseConflict`/`CourseClassConflict` = 0 over `inserted`.
- [ ] Locked schedules in the request are preserved (their slots are never reassigned).
- [ ] `POST /api/timetable/generate` (async) returns `{ jobId }` fast (<200 ms); `GET /api/sse/progress?jobId=` streams `running` events with increasing `progress` then one `completed` event carrying the result.
- [ ] SSE connection closes cleanly on completion and on client disconnect (`emitters` map empties — assert size 0 after).
- [ ] `POST /api/timetable/save` persists `Result` rows (valid=1 placed, valid=0 notInserted) and triggers `revalidate` (slot_acts refreshed, `validate_lock` opened).
- [ ] `GET /api/timetable/init-schedule` and `/schedule/{id}` return `{ inserted, notInserted }` matching persisted results.
- [ ] `GET /api/timetable/data` returns the activities/rooms maps consumable by the frontend grid.

---

## 15. Risks & notes

| # | Risk | Mitigation |
|---|------|-----------|
| **R1** | **Contiguous-slot-id assumption (A1).** `getSlotStartToDuration(start, duration)` returns `start, start+1, …`. If the DB's `slots` are not seeded in day-major/room-major/hour-contiguous order, a start slot's neighbours may belong to a different room/day. | This is exactly the Laravel assumption (`getResultTimeIds`: `id=(day-1)*17+1+hour-7`). Verify the `slots` seeding matches before trusting results; add an assertion in `SlotsWithPriority.resolveSlots` that the resolved slots share `roomId` and consecutive hours, else drop the candidate. |
| **R2** | **Correctness of the ported GA.** The source is a *partial, buggy* rewrite; even after §3 fixes it had no passing tests. `Problem.removeFreeSlotByActivity` is a no-op, so mutation can re-pick a slot that conflicts with a *different* activity's lecturer — the fitness function penalises it, but the search may converge slowly. | The hard constraints (§8.5–8.6) catch every conflict at fitness-evaluation time, so the *final* `setSchedule` (which only schedules non-conflicting activities) stays correct; conflicted ones fall into `conflicts`/`notInserted`. Tune `generations`/`populationSize`. Add a `TimetableServiceTest` (Phase 10) asserting a known small instance schedules with 0 hard violations. |
| **R3** | **SSE auth.** Browser `EventSource` cannot send `Authorization: Bearer`. | Accept `?token=` on `/api/sse/**` only (§12.2), or use the fetch-based SSE polyfill that supports headers. |
| **R4** | **Async + `@Transactional(readOnly=true)`.** `generateSync` runs inside the `ga-` thread; lazy associations (`course`, `room`, `time`) must be initialised before the entity leaves its transaction. | The mapping (`toActivity`/`toSlot`/`toRoom`) touches every needed association *inside* `generateSync`'s transaction, producing detached POJO models — nothing lazy escapes. `findAllForScheduling` fetch-joins `course`+`activityType`. Confirm `Slot.time` is `EAGER` (it is) and `Slot.room`/`Room.roomType` are touched in the loop. |
| **R5** | **Concurrent generate runs.** Two admins triggering `generate` share the singleton stateless beans (fine) but write `Result` on save. | GA state is per-run (A2), so concurrent *generation* is safe; `save` is `@Transactional` and last-write-wins per semester (same as Laravel). Executor caps concurrency at 2. |
| **R6** | **Faculty scoping** not yet centralised (see §9 note). | Wire `Jurusan::jurusanIds()` equivalent before Phase 10 end-to-end tests; until then results include all jurusans. |

---

*End of Phase 8 plan. Proceed file-by-file in this order: models → genetic core → constraints → problem → `GaEngineFactory` → `SseService`/`SseController` → `TimetableService` → `TimetableController` → config/repository edits → smoke test via `?wait=true`, then wire SSE.*
