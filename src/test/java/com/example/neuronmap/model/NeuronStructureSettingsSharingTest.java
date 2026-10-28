package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Перевіряє спільне посилання внутрішніх нейронів на налаштування структури та від'єднання без втрати значень. */
class NeuronStructureSettingsSharingTest {

    /** Перевіряє, що кожне поле налаштувань учасника читається з контейнера й змінює саме його стан. */
    @Test
    void directMemberUsesStructureSettingsAsSingleSourceOfTruth() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 20.0, 20.0);
        structure.setName("Core");
        structure.setParameters(new NeuronParameters(7, 3, 4, 2, NeuronActivationMode.SIGNAL_STRENGTH));
        member.setName("Previous member name");
        member.setParameters(new NeuronParameters(2, 1, 1, 1));

        assertTrue(model.addStructureMember(structure.id(), member.id()));

        assertEquals("Core", member.name());
        assertSame(structure.parameters(), member.parameters());
        member.setName("Renamed structure");
        member.setParameters(new NeuronParameters(9, 5, 6, 3, NeuronActivationMode.SIGNAL_STRENGTH));
        assertEquals("Renamed structure", structure.name());
        assertSame(structure.parameters(), member.parameters());
        assertEquals(9, structure.signalStrength());
        assertEquals(5, structure.activationThreshold());
        assertEquals(6, structure.activeTicks());
        assertEquals(3, structure.delayTicks());

        structure.setActivationMode(NeuronActivationMode.SIGNAL_COUNT);
        structure.setActivationThreshold(75);
        assertEquals(NeuronActivationMode.SIGNAL_COUNT, member.activationMode());
        assertEquals(75, member.activationThreshold());
    }

    /** Перевіряє успадкування налаштувань через вкладену структуру та збереження їх після від'єднання. */
    @Test
    void nestedMembersFollowOuterSettingsUntilNestedStructureIsDetached() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron outer = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron inner = model.createNeuron(NeuronType.STRUCTURE, 10.0, 10.0);
        Neuron leaf = model.createNeuron(NeuronType.INHIBITORY, 20.0, 20.0);
        inner.setName("Inner before linking");
        inner.setParameters(new NeuronParameters(4, 2, 3, 2));
        leaf.setName("Leaf before linking");
        leaf.setParameters(new NeuronParameters(2, 1, 2, 1));
        assertTrue(model.addStructureMember(inner.id(), leaf.id()));
        outer.setName("Outer settings");
        outer.setParameters(new NeuronParameters(8, 4, 5, 3));

        assertTrue(model.addStructureMember(outer.id(), inner.id()));
        assertEquals("Outer settings", inner.name());
        assertEquals("Outer settings", leaf.name());
        assertSame(outer.parameters(), inner.parameters());
        assertSame(outer.parameters(), leaf.parameters());

        outer.setParameters(new NeuronParameters(11, 6, 7, 4));
        assertEquals(11, leaf.signalStrength());
        assertEquals(7, leaf.activeTicks());

        assertTrue(model.removeStructureMember(outer.id(), inner.id()));
        assertEquals("Outer settings", inner.name());
        assertSame(inner.parameters(), leaf.parameters());
        assertNotSame(outer.parameters(), inner.parameters());
        outer.setName("New outer settings");
        outer.setParameters(new NeuronParameters(13, 7, 8, 5));
        assertEquals("Outer settings", leaf.name());
        assertEquals(11, leaf.signalStrength());
        assertEquals(6, leaf.activationThreshold());
    }

    /** Перевіряє, що видалення контейнера залишає колишнім учасникам поточні налаштування. */
    @Test
    void deletingStructurePreservesCurrentSettingsForFormerMembers() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0.0, 0.0);
        Neuron member = model.createNeuron(NeuronType.EXCITATORY, 10.0, 10.0);
        structure.setName("Persistent settings");
        structure.setParameters(new NeuronParameters(6, 3, 4, 2));
        assertTrue(model.addStructureMember(structure.id(), member.id()));

        assertSame(structure.parameters(), member.parameters());
        model.removeNeuron(structure.id());

        assertNull(model.structure(structure.id()));
        assertEquals("Persistent settings", member.name());
        assertEquals(new NeuronParameters(6, 3, 4, 2), member.parameters());
        member.setSignalStrength(12);
        assertEquals(6, structure.signalStrength());
        assertEquals(12, member.signalStrength());
    }
}
