package com.example.neuronmap.service;

import com.example.neuronmap.model.Neuron;
import com.example.neuronmap.model.NeuronGroup;
import com.example.neuronmap.model.NeuronPresentation;
import com.example.neuronmap.model.NeuronType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Копіює й вставляє нейрони та групи зі збереженням потрібних даних.
 */
public final class NeuronClipboardService {

    private final NeuronService neuronService;
    private final GroupService groupService;

    private ClipboardContent content;

    /**
     * Повертає результат операції «нейрон буфер обміну служба».
     *
     * @param neuronService значення, що визначає нейрон служба для цієї операції.
     *
     * @param groupService значення, що визначає група служба для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public NeuronClipboardService(
            NeuronService neuronService,
            GroupService groupService
    ) {
        if (neuronService == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("neuronService must not be null");
        }
        if (groupService == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException("groupService must not be null");
        }
        this.neuronService = neuronService;
        this.groupService = groupService;
    }

    /**
     * Видаляє або скидає дані, повʼязані з «потрібні дані».
     */
    public void clear() {
        content = null;
    }

    /**
     * Перевіряє, чи виконується умова «відповідну операцію».
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean hasContent() {
        return content != null && !content.items().isEmpty();
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @return знайдене значення або порожній Optional, якщо результату немає.
     */
    public Optional<ClipboardContent> content() {
        return Optional.ofNullable(content);
    }

    /**
     * Повертає результат операції «копіювання».
     *
     * @param selectedNeuronIds ідентифікатори вибраних нейронів.
     *
     * @return true, якщо умову виконано або операція завершилася успішно; інакше false.
     */
    public boolean copy(Set<String> selectedNeuronIds) {
        if (selectedNeuronIds == null || selectedNeuronIds.isEmpty()) {
            clear();
            return false;
        }

        LinkedHashSet<String> selectedIds =
                new LinkedHashSet<>(selectedNeuronIds);

        boolean grouped = false;
        Set<String> idsToCopy;

        if (selectedIds.size() == 1) {
            String neuronId = selectedIds.iterator().next();
            NeuronGroup group = groupService.containing(neuronId);
            if (group != null) {
                idsToCopy = new LinkedHashSet<>(group.memberIds());
                grouped = true;
            } else {
                idsToCopy = new LinkedHashSet<>(selectedIds);
            }
        } else {
            idsToCopy = new LinkedHashSet<>(selectedIds);
            Set<String> idsForGroupCheck = Set.copyOf(idsToCopy);
            grouped = neuronService.model().groups().stream()
                    .anyMatch(group -> group.memberIds().equals(idsForGroupCheck));
        }

        List<NeuronSnapshotSource> sources = idsToCopy.stream()
                .map(neuronService::find)
                .filter(neuron -> neuron != null)
                .map(neuron -> new NeuronSnapshotSource(
                        neuron,
                        neuronService.presentation(neuron.id())
                ))
                .filter(source -> source.presentation() != null)
                .toList();

        if (sources.isEmpty()) {
            clear();
            return false;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (NeuronSnapshotSource source : sources) {
            NeuronPresentation presentation = source.presentation();
            minX = Math.min(minX, presentation.x());
            maxX = Math.max(maxX, presentation.x());
            minY = Math.min(minY, presentation.y());
            maxY = Math.max(maxY, presentation.y());
        }

        double anchorX = (minX + maxX) / 2.0;
        double anchorY = (minY + maxY) / 2.0;

        content = new ClipboardContent(
                createItems(sources, anchorX, anchorY),
                grouped
        );
        return true;
    }

    /**
     * Повертає результат операції «вставлення».
     *
     * @param anchorX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param anchorY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    public List<Neuron> paste(double anchorX, double anchorY) {
        if (!hasContent()) {
            return List.of();
        }

        LinkedHashSet<String> pastedIds = new LinkedHashSet<>();
        List<Neuron> pastedNeurons = new ArrayList<>(content.items().size());

        for (NeuronData item : content.items()) {
            Neuron neuron = neuronService.create(
                    item.type(),
                    anchorX + item.offsetX(),
                    anchorY + item.offsetY()
            );

            neuron.setSignalStrength(item.signalStrength());
            neuron.setActivationThreshold(item.activationThreshold());

            NeuronPresentation presentation =
                    neuronService.presentation(neuron.id());
            if (presentation != null) {
                presentation.setRotationDegrees(item.rotationDegrees());
                presentation.setDirectionReversed(item.directionReversed());
            }

            pastedIds.add(neuron.id());
            pastedNeurons.add(neuron);
        }

        if (content.grouped() && pastedIds.size() >= 2) {
            groupService.create(pastedIds);
        }

        return List.copyOf(pastedNeurons);
    }

    /**
     * Компонент ClipboardContent у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «буфер обміну».
     *
     * @param items значення, що визначає відповідну операцію для цієї операції.
     *
     * @param grouped значення, що визначає згрупований для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public record ClipboardContent(
            List<NeuronData> items,
            boolean grouped
    ) {
        public ClipboardContent {
            items = List.copyOf(items);
        }
    }

    /**
     * Створює обʼєкт із переданих даних «відповідну операцію».
     *
     * @param sources значення, що визначає відповідну операцію для цієї операції.
     *
     * @param anchorX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param anchorY значення, що визначає відповідну операцію для цієї операції.
     *
     * @return колекцію результатів; якщо елементів немає, колекція порожня.
     */
    private List<NeuronData> createItems(
            List<NeuronSnapshotSource> sources,
            double anchorX,
            double anchorY
    ) {
        List<NeuronData> items = new ArrayList<>(sources.size());

        for (NeuronSnapshotSource source : sources) {
            Neuron neuron = source.neuron();
            NeuronPresentation presentation = source.presentation();
            items.add(new NeuronData(
                    neuron.type(),
                    neuron.signalStrength(),
                    neuron.activationThreshold(),
                    presentation.x() - anchorX,
                    presentation.y() - anchorY,
                    presentation.rotationDegrees(),
                    presentation.directionReversed()
            ));
        }

        return List.copyOf(items);
    }

    /**
     * Компонент NeuronData у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    /**
     * Повертає результат операції «нейрон дані».
     *
     * @param type тип обʼєкта.
     *
     * @param signalStrength сила сигналу нейрона.
     *
     * @param activationThreshold поріг активації нейрона.
     *
     * @param offsetX значення, що визначає відповідну операцію для цієї операції.
     *
     * @param offsetY значення, що визначає відповідну операцію для цієї операції.
     *
     * @param rotationDegrees значення, що визначає обертання градуси для цієї операції.
     *
     * @param directionReversed значення, що визначає напрямок для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public record NeuronData(
            NeuronType type,
            int signalStrength,
            int activationThreshold,
            double offsetX,
            double offsetY,
            double rotationDegrees,
            boolean directionReversed
    ) {
    }

    /**
     * Повертає результат операції «нейрон знімок джерело».
     *
     * @param neuron нейрон, над яким виконується операція.
     *
     * @param presentation значення, що визначає представлення для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    /**
     * Компонент NeuronSnapshotSource у складі NeuronMap. Його призначення та параметри операцій описані над відповідними методами.
     */
    private record NeuronSnapshotSource(
            Neuron neuron,
            NeuronPresentation presentation
    ) {
    }
}
