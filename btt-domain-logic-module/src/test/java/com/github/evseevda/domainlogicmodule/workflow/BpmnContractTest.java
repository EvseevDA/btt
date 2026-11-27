package com.github.evseevda.domainlogicmodule.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Modifier;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class BpmnContractTest {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ZEEBE = "http://camunda.org/schema/zeebe/1.0";

    @Test
    void allPackagedWorkflowsUseTheDeclaredMessageNamesAndOrdinaryStartEvents() throws Exception {
        var parser = DocumentBuilderFactory.newInstance();
        parser.setNamespaceAware(true);
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath:bpmn/blm_*.bpmn");
        assertEquals(6, resources.length);

        var names = new HashSet<String>();
        for (var resource : resources) {
            try (var stream = resource.getInputStream()) {
                var model = parser.newDocumentBuilder().parse(stream);
                var process = (Element) model.getElementsByTagNameNS(BPMN, "process").item(0);
                assertEquals("true", process.getAttribute("isExecutable"));
                assertEquals(process.getAttribute("id") + "_v1.bpmn", resource.getFilename());
                assertEquals(0, process.getElementsByTagNameNS(BPMN, "userTask").getLength());
                var start = process.getElementsByTagNameNS(BPMN, "startEvent");
                assertEquals(1, start.getLength());
                assertEquals(0, ((Element) start.item(0)).getElementsByTagNameNS(BPMN, "messageEventDefinition").getLength());

                NodeList definitions = model.getElementsByTagNameNS(BPMN, "message");
                var ids = new HashSet<String>();
                for (int i = 0; i < definitions.getLength(); i++) {
                    var message = (Element) definitions.item(i);
                    ids.add(message.getAttribute("id"));
                    names.add(message.getAttribute("name"));
                    var key = (Element) message.getElementsByTagNameNS(ZEEBE, "subscription").item(0);
                    assertNotNull(key);
                    assertEquals("=requestId", key.getAttribute("correlationKey"));
                }
                var references = process.getElementsByTagNameNS(BPMN, "messageEventDefinition");
                var referenced = new HashSet<String>();
                for (int i = 0; i < references.getLength(); i++) {
                    referenced.add(((Element) references.item(i)).getAttribute("messageRef"));
                }
                assertEquals(ids, referenced, resource.toString());
            }
        }

        var constants = new HashSet<String>();
        for (var field : BpmnEventNames.class.getFields()) {
            if (field.getType().equals(String.class) && Modifier.isStatic(field.getModifiers())) {
                constants.add((String) field.get(null));
            }
        }
        assertEquals(constants, names);
    }
}
