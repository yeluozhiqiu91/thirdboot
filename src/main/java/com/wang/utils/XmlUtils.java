package com.wang.utils;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.*;

/**
 * @author weichao <gorilla@aliyun.com>
 * @date 2016-1-9
 */
@SuppressWarnings("rawtypes")
public class XmlUtils {
    private static Logger logger = LoggerFactory.getLogger(XmlUtils.class);
    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    public static Map parseToMap(String content, String... nodePathExpressions) {

        return parseToMap(content, false, nodePathExpressions);

    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    @SuppressWarnings("unchecked")
    public static Map parseToMap(String content, boolean hasChildren, String... nodePathExpressions) {

        try {
            Document document = createDocument(content);
            Map returnMap = new HashMap();
            for (String path : nodePathExpressions) {
                List<Element> selectedNodes = document.selectNodes(path);
                if (selectedNodes.size() > 1) {
                    returnMap.put(selectedNodes.get(0).getName(), parseToList(selectedNodes));
                } else {
                    transferToMap(returnMap, selectedNodes, hasChildren);
                }
            }
            return returnMap;
        } catch (Exception e) {
            logger.error(e.getMessage(),e);
        }

        return null;

    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    @SuppressWarnings("unchecked")
    public static List parseToList(String content, String nodePathExpression) {

        try {
            Document document = createDocument(content);
            List<Element> selectedNodes = document.selectNodes(nodePathExpression);
            return parseToList(selectedNodes);
        } catch (DocumentException e) {
            logger.error(e.getMessage(),e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static List parseToList(List<Element> selectedNodes) throws DocumentException {

        if (!selectedNodes.isEmpty()) {
            List returnNodes = new ArrayList(selectedNodes.size());
            Element el = null;
            Iterator<Element> iter = selectedNodes.iterator();
            while (iter.hasNext()) {
                el = iter.next();
                returnNodes.add(transferToMap(new HashMap(), el, true));
            }
            return returnNodes;
        }

        return Collections.emptyList();
    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    public static Map<String, String> parseToMap(String content) {

        Map<String, String> returnMap = new HashMap<String, String>();

        try {
            Document document = createDocument(content);
            parseToMap(returnMap, document.getRootElement());
        } catch (DocumentException ex) {
            ex.printStackTrace();
        }

        return returnMap;

    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    public static Document createDocument(String content) throws DocumentException {

        Document document = new SAXReader().read(new StringReader(content));

        return document;

    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    public static Map<String, String> parseToMap(Map<String, String> returnMap, Element element) throws DocumentException {


        Iterator<?> iter = element.elements().iterator();

        while (iter.hasNext()) {
            Element el = (Element) iter.next();
            if (!el.isTextOnly()) {
                parseToMap(returnMap, el);
            } else {
                returnMap.put(el.getName(), el.getText());
            }
        }

        return returnMap;

    }

    /**
     * @param nodes
     * @return
     */
    private static Map<String, String> transferToMap(Map<String, String> result, List nodes, boolean hasChildren) {

        if (nodes == null || nodes.size() == 0) {
            return null;
        }

        Iterator iter = nodes.iterator();

        while (iter.hasNext()) {
            Element node = (Element) iter.next();
            if (hasChildren && !node.isTextOnly()) {
                transferToMap(result, node.elements(), true);
            } else {
                result.put(node.getName(), node.getText());
            }

        }

        return result;

    }

    /**
     * @param nodes
     * @return
     */
    private static Map<String, String> transferToMap(Map<String, String> result, Element element, boolean hasChildren) {

        if (!element.isTextOnly()) {
            return transferToMap(result, element.elements(), hasChildren);
        }

        result.put(element.getName(), element.getText());

        return result;

    }

    /**
     * @param content
     * @param nodePathExpression
     * @return
     * @throws DocumentException
     */
    public static String singleValue(String content, String nodePathExpression) {

        Document document;

        try {
            document = new SAXReader().read(new StringReader(content));
            List selectedNodes = document.selectNodes(nodePathExpression);
            if (!selectedNodes.isEmpty()) {
                return ((Element) selectedNodes.get(0)).getTextTrim();
            }
        } catch (DocumentException e) {
            logger.error(e.getMessage(),e);
        }

        return null;

    }

    public static void main(String[] args) {

        try {
            //System.out.println(parseToMap("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><body><code><a>f</a></code><code><a>c</a></code></body>",
            // "/body/code/a"));
            System.out.println(parseToMap("<ns1:queryOrderResponse xmlns:ns1=\"http://zz.protocol.intf.tisson.cn\"><resultCode>0</resultCode><resultCount>" +
                    "0</resultCount><orderMessageArray /></ns1:queryOrderResponse>"));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            logger.error(e.getMessage(),e);
        }
    }

}
