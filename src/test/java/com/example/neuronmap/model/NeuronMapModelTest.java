package com.example.neuronmap.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевіряє створення нейронів, правила напрямлених зв’язків, видалення нейрона та очищення членства в групах.
 */
class NeuronMapModelTest {

    /**
     * Перевіряє, що створення нейрона додає окремий об’єкт його візуального подання.
     */
    @Test
    void createNeuronCreatesSeparatePresentation() {
        NeuronMapModel model = new NeuronMapModel();

        Neuron neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                120.0,
                80.0,
                37.5
        );

        NeuronPresentation presentation =
                model.presentation(neuron.id());

        assertNotNull(presentation);
        assertSame(neuron, presentation.neuron());
        assertEquals(120.0, presentation.x());
        assertEquals(80.0, presentation.y());
        assertEquals(37.5, presentation.rotationDegrees());
        assertEquals(0, neuron.activation());
    }

    /**
     * Перевіряє напрямленість, унікальність зв’язків і заборону зв’язку нейрона із самим собою.
     */
    @Test
    void connectionsAreDirectedUniqueAndCannotBeSelfLoops() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.INHIBITORY,
                100,
                0
        );

        assertTrue(model.createConnection(a.id(), b.id()));
        assertFalse(model.createConnection(a.id(), b.id()));
        assertFalse(model.createConnection(a.id(), a.id()));
        assertFalse(model.hasConnection(b.id(), a.id()));
        assertEquals(1, model.connections().size());
    }

    /**
     * Перевіряє, що навіть низькорівневе додавання зв’язку відхиляє петлю на одному нейроні.
     */
    @Test
    void lowLevelConnectionImportAlsoRejectsSelfLoops() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron neuron = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );

        model.addConnection(
                "self-loop",
                neuron.id(),
                neuron.id()
        );

        assertEquals(0, model.connections().size());
    }

    /**
     * Перевіряє видалення зв’язків між двома нейронами в обох напрямках.
     */
    @Test
    void removesContactInBothDirections() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.EXCITATORY,
                100,
                0
        );
        Neuron c = model.createNeuron(
                NeuronType.EXCITATORY,
                200,
                0
        );

        assertTrue(model.createConnection(a.id(), b.id()));
        assertTrue(model.createConnection(b.id(), a.id()));
        assertTrue(model.createConnection(a.id(), c.id()));

        assertEquals(
                2,
                model.removeConnectionsBetween(a.id(), b.id())
        );

        assertFalse(model.hasConnection(a.id(), b.id()));
        assertFalse(model.hasConnection(b.id(), a.id()));
        assertTrue(model.hasConnection(a.id(), c.id()));
    }

    /**
     * Перевіряє очищення подання, зв’язків і членства у групі після видалення нейрона.
     */
    @Test
    void deletingNeuronRemovesPresentationConnectionsAndItsGroupMembership() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron a = model.createNeuron(
                NeuronType.EXCITATORY,
                0,
                0
        );
        Neuron b = model.createNeuron(
                NeuronType.INHIBITORY,
                100,
                0
        );
        Neuron c = model.createNeuron(
                NeuronType.EXCITATORY,
                200,
                0
        );

        model.createConnection(a.id(), b.id());
        model.createConnection(b.id(), c.id());
        model.createConnection(c.id(), b.id());
        model.createGroup(
                Set.of(a.id(), b.id(), c.id())
        );

        model.removeNeuron(b.id());

        assertNull(model.neuron(b.id()));
        assertNull(model.presentation(b.id()));
        assertTrue(model.connections().isEmpty());

        NeuronGroup remainingGroup =
                model.groupContaining(a.id());

        assertNotNull(remainingGroup);
        assertEquals(
                Set.of(a.id(), c.id()),
                remainingGroup.memberIds()
        );
        assertNull(model.groupContaining(b.id()));
    }

    /**
     * Перевіряє, що структури з’єднуються лише з іншими структурами, а учасники — поза спільною областю.
     */
    @Test
    void structureConnectionsRespectBoundariesAndAllowExternalTargets() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structureA = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron memberA = model.createNeuron(NeuronType.EXCITATORY, 10, 10);
        Neuron siblingA = model.createNeuron(NeuronType.INHIBITORY, 20, 20);
        Neuron structureB = model.createNeuron(NeuronType.STRUCTURE, 100, 0);
        Neuron memberB = model.createNeuron(NeuronType.EXCITATORY, 110, 10);
        Neuron boardNeuron = model.createNeuron(NeuronType.INHIBITORY, 200, 0);

        assertTrue(model.addStructureMember(structureA.id(), memberA.id()));
        assertTrue(model.addStructureMember(structureA.id(), siblingA.id()));
        assertTrue(model.addStructureMember(structureB.id(), memberB.id()));

        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(structureA.id(), memberA.id()));
        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(memberA.id(), structureA.id()));
        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(memberA.id(), siblingA.id()));
        assertEquals(ConnectionValidationResult.STRUCTURE_ENDPOINT_TYPE_MISMATCH,
                model.validateConnection(structureA.id(), boardNeuron.id()));
        assertEquals(ConnectionValidationResult.STRUCTURE_ENDPOINT_TYPE_MISMATCH,
                model.validateConnection(boardNeuron.id(), structureB.id()));
        assertEquals(ConnectionValidationResult.VALID,
                model.validateConnection(structureA.id(), structureB.id()));
        assertEquals(ConnectionValidationResult.VALID,
                model.validateConnection(memberA.id(), memberB.id()));
        assertEquals(ConnectionValidationResult.VALID,
                model.validateConnection(memberA.id(), boardNeuron.id()));

        assertTrue(model.createConnection(structureA.id(), structureB.id()));
        assertTrue(model.createConnection(memberA.id(), memberB.id()));
        assertTrue(model.createConnection(memberA.id(), boardNeuron.id()));
        assertFalse(model.createConnection(structureA.id(), boardNeuron.id()));
    }

    /**
     * Перевіряє межі зв’язків для вкладених структур і заборону зв’язків у спільній області батьківської структури.
     */
    @Test
    void nestedStructureMembersCannotConnectInsideTheirOuterStructure() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron outerStructure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron innerStructure = model.createNeuron(NeuronType.STRUCTURE, 20, 20);
        Neuron innerMember = model.createNeuron(NeuronType.EXCITATORY, 30, 30);
        Neuron outerSibling = model.createNeuron(NeuronType.INHIBITORY, 40, 40);
        Neuron externalStructure = model.createNeuron(NeuronType.STRUCTURE, 200, 0);
        Neuron externalBoardNeuron = model.createNeuron(NeuronType.EXCITATORY, 300, 0);

        assertTrue(model.addStructureMember(outerStructure.id(), innerStructure.id()));
        assertTrue(model.addStructureMember(innerStructure.id(), innerMember.id()));
        assertTrue(model.addStructureMember(outerStructure.id(), outerSibling.id()));

        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(outerStructure.id(), innerStructure.id()));
        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(innerMember.id(), outerSibling.id()));
        assertEquals(ConnectionValidationResult.SAME_STRUCTURE_SCOPE,
                model.validateConnection(innerStructure.id(), outerSibling.id()));
        assertEquals(ConnectionValidationResult.VALID,
                model.validateConnection(innerStructure.id(), externalStructure.id()));
        assertEquals(ConnectionValidationResult.VALID,
                model.validateConnection(innerMember.id(), externalBoardNeuron.id()));

        assertTrue(model.createConnection(innerStructure.id(), externalStructure.id()));
        assertTrue(model.createConnection(innerMember.id(), externalBoardNeuron.id()));
        assertFalse(model.createConnection(innerMember.id(), outerSibling.id()));
    }

    /**
     * Перевіряє, що імпорт зв’язків не обходить правила структур і що дубльовані зв’язки не створюються.
     */
    @Test
    void lowLevelConnectionCreationRejectsStructuralViolationsAndDuplicates() {
        NeuronMapModel model = new NeuronMapModel();
        Neuron structure = model.createNeuron(NeuronType.STRUCTURE, 0, 0);
        Neuron firstMember = model.createNeuron(NeuronType.EXCITATORY, 10, 10);
        Neuron secondMember = model.createNeuron(NeuronType.INHIBITORY, 20, 20);
        assertTrue(model.addStructureMember(structure.id(), firstMember.id()));
        assertTrue(model.addStructureMember(structure.id(), secondMember.id()));

        assertEquals(ConnectionValidationResult.INVALID_ENDPOINT,
                model.validateConnection(firstMember.id(), "missing"));
        assertEquals(ConnectionValidationResult.SELF_CONNECTION,
                model.validateConnection(firstMember.id(), firstMember.id()));
        assertFalse(model.createConnection(firstMember.id(), secondMember.id()));

        model.addConnection("same-scope", firstMember.id(), secondMember.id());
        model.addConnection("structure-to-member", structure.id(), firstMember.id());
        assertTrue(model.connections().isEmpty());

        Neuron outsideA = model.createNeuron(NeuronType.EXCITATORY, 100, 0);
        Neuron outsideB = model.createNeuron(NeuronType.INHIBITORY, 200, 0);
        assertTrue(model.createConnection(outsideA.id(), outsideB.id()));
        assertEquals(ConnectionValidationResult.ALREADY_EXISTS,
                model.validateConnection(outsideA.id(), outsideB.id()));
        assertFalse(model.createConnection(outsideA.id(), outsideB.id()));
        assertEquals(1, model.connections().size());
    }
}
