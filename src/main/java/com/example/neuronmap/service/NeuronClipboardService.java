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

/** Owns neuron copy/paste data and the corresponding application use case. */
public final class NeuronClipboardService {

    private final NeuronService neuronService;
    private final GroupService groupService;

    private ClipboardContent content;

    public NeuronClipboardService(
            NeuronService neuronService,
            GroupService groupService
    ) {
        if (neuronService == null) {
            throw new IllegalArgumentException("neuronService must not be null");
        }
        if (groupService == null) {
            throw new IllegalArgumentException("groupService must not be null");
        }
        this.neuronService = neuronService;
        this.groupService = groupService;
    }

    public void clear() {
        content = null;
    }

    public boolean hasContent() {
        return content != null && !content.items().isEmpty();
    }

    public Optional<ClipboardContent> content() {
        return Optional.ofNullable(content);
    }

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

    public record ClipboardContent(
            List<NeuronData> items,
            boolean grouped
    ) {
        public ClipboardContent {
            items = List.copyOf(items);
        }
    }

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

    private record NeuronSnapshotSource(
            Neuron neuron,
            NeuronPresentation presentation
    ) {
    }
}
