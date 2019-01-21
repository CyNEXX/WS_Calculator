/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cynexx.ws_calculator;

import Models.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.ejb.Stateless;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

/**
 *
 * @author CyNEXX
 */
@WebService(serviceName = "CalculatorWS")
@Stateless()
public class CalculatorWS {

    private final static String EMPTY_MSG = "No operations made so far";
    private final static String DEFAULT_HISTORY_FOLDER = "history_log";
    private final static String DEFAULT_HISTORY_XML = "./history_log/history.xml";
    private final static int MAX_HISTORY_ENTRIES = 6;
    private final static DateTimeFormatter DT_FRMT = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy");

    private String operationSign;
    private double operandA;
    private double operandB;
    private double result;
    private StringBuilder sb;
    private File historyFile;
    private boolean freshHistoryFile;

    private HttpServletRequest req;
    private MessageContext mc;

    /**
     * Web service operation
     */
    static {
        System.setProperty("javax.xml.soap.SAAJMetaFactory", "com.sun.xml.messaging.saaj.soap.SAAJMetaFactoryImpl");
    }

    {
        try {
            checkHistory();
        } catch (IOException ex) {
            Logger.getLogger(CalculatorWS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Resource
    WebServiceContext wsc;

    @WebMethod(operationName = "calcThis")
    public String calcThis(@WebParam(name = "operandA") double operandA, @WebParam(name = "operandB") double operandB, @WebParam(name = "operator") byte operator) {
        try {
            mc = wsc.getMessageContext();
            req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);
            String tempIp = req.getRemoteAddr();

            this.operandA = operandA;
            this.operandB = operandB;
            switch (operator) {
                case 1: {
                    operationSign = " + ";
                    result = operandA + operandB;
                    break;
                }
                case 2: {
                    operationSign = " - ";
                    result = operandA - operandB;
                    break;
                }
                case 3: {
                    operationSign = " X ";
                    result = operandA * operandB;
                    break;
                }
                case 4: {
                    operationSign = " ÷ ";
                    if (operandB == 0) {
                        return "Division by zero";
                    }
                    result = operandA / operandB;
                    break;
                }
                case 5: {
                    operationSign = " POW ";
                    result = Math.pow(operandA, operandB);
                    break;
                }// power
                case 6: {
                    operationSign = " SQ_RT ";
                    result = Math.sqrt(operandA);
                    break;
                }//root
            }
            addToHistory(tempIp);
        } catch (Exception e) {
            return "Invalid elements";
        }
        return Double.toString(result);
    }

    /**
     * Web service operation
     *
     * @return
     * @throws java.io.FileNotFoundException
     */
    @WebMethod(operationName = "getHistory")
    public String getHistory() throws FileNotFoundException {

        mc = wsc.getMessageContext();
        req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST);


        sb = new StringBuilder();
        try {
            JAXBContext jaxbCtx = JAXBContext.newInstance("Models");
            Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();

            Root tempH = (Root) unmarshaller.unmarshal(historyFile);

            List<Historyoperation> operationList = tempH.getHistoryoperation();

            for (Historyoperation operation : operationList) {
                sb.append(operation.getHistoryelement());
            }

        } catch (JAXBException e) {

            sb.append(EMPTY_MSG);
        }

        return sb.toString();
    }

    private void addToHistory(String ip) {

        try {

            if (!freshHistoryFile) { 
                JAXBContext jaxbCtx = JAXBContext.newInstance("Models");
                Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
                historyFile = new File(DEFAULT_HISTORY_XML);
                Root loadedRoot = (Root) unmarshaller.unmarshal(historyFile);
                ObjectFactory objectFactory = new ObjectFactory();
                Historyoperation ho = objectFactory.createHistoryoperation();

                ho.setHistoryelement(makeString(ip));

                Marshaller marshaller = jaxbCtx.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

                if (loadedRoot.getHistoryoperation().size() < MAX_HISTORY_ENTRIES) {
                    loadedRoot.getHistoryoperation().add(ho);
                    System.out.println(" [addToHistory] - Added to history");
                } else if (loadedRoot.getHistoryoperation().size() == MAX_HISTORY_ENTRIES) {
                    ArrayList<Historyoperation> tempArrayList = new ArrayList<>();
                    System.out.println(" [addToHistory] - Created new ArrayList");
                    loadedRoot.getHistoryoperation().remove(1);

                    for (int i = 1; i < loadedRoot.getHistoryoperation().size(); i++) {
                        System.out.println(i);
                        System.out.println(loadedRoot.getHistoryoperation().get(i));
                        tempArrayList.add(loadedRoot.getHistoryoperation().get(i));
                    }

                    for (int i = 1; i < tempArrayList.size(); i++) {
                        loadedRoot.getHistoryoperation().set(i, tempArrayList.get(i - 1));
                    }
                    loadedRoot.getHistoryoperation().add(ho);
                }
                marshaller.marshal(loadedRoot, historyFile);
            }

        } catch (JAXBException e) {
            writeEmptyEntry();
        }

    }

    private boolean checkHistory() throws IOException {
        boolean historyOk = false;
        freshHistoryFile = false;

        try {
            File newFolder = new File(DEFAULT_HISTORY_FOLDER);

            if (!newFolder.exists() || !newFolder.isDirectory()) {
                newFolder.mkdir();
            }
            historyFile = new File(DEFAULT_HISTORY_XML);

            if (!historyFile.exists()) {
                writeEmptyEntry();
            }

            if (!freshHistoryFile) {
                JAXBContext jaxbCtx = JAXBContext.newInstance("Models");
                Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
                Root tempH = (Root) unmarshaller.unmarshal(historyFile);

                List<Historyoperation> operationList = tempH.getHistoryoperation();

                if (operationList.isEmpty()) {

                    freshHistoryFile = true;
                    sb.append("Empty History List. No operations made so far.");
                }
            }
            historyOk = true;
        } catch (JAXBException e) {
        }

        return historyOk;
    }

    private void writeEmptyEntry() {
        try {

            ObjectFactory objectFactory = new ObjectFactory();
            Historyoperation ho = objectFactory.createHistoryoperation();

            ho.setHistoryelement(DEFAULT_HISTORY_XML + " created on: " + timeNow() + ". " + EMPTY_MSG + ";");

            JAXBContext jaxbCtx = JAXBContext.newInstance("Models");

            Marshaller marshaller = jaxbCtx.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            Root root = new Root();
            root.getHistoryoperation().add(ho);
            marshaller.marshal(root, historyFile);
        } catch (JAXBException ex) {
            Logger.getLogger(CalculatorWS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String makeString(String ip) {
        StringBuilder tempSb = new StringBuilder();
        tempSb.append("IP: ").append(ip)
                .append(" @ ")
                .append(timeNow())
                .append(" did: ")
                .append(operandA)
                .append(" ")
                .append(operationSign);
        if (!operationSign.contains("SQ_RT")) {
            tempSb.append(" ").append(operandB);
        }
        tempSb.append(" = ")
                .append(result).append(";");
        return tempSb.toString();
    }

    private String timeNow() {
        return LocalDateTime.now().format(DT_FRMT);
    }

}
