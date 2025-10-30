package com.liuzhihang.doc.view.dto;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.TemplateSettings;
import com.liuzhihang.doc.view.constant.FieldTypeConstant;
import com.liuzhihang.doc.view.enums.FrameworkEnum;
import com.liuzhihang.doc.view.enums.ParamTypeEnum;
import com.liuzhihang.doc.view.utils.SpringPsiUtils;
import com.liuzhihang.doc.view.utils.VelocityUtils;
import lombok.Data;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DocView 的模版 用来使用 Velocity 生成内容
 * <p>
 * Velocity 会根据 get 方法 获取值, 不提供 set 方法
 *
 * @author liuzhihang
 * @date 2020/11/21 16:39
 */
@Data
public class DocViewData {

    /**
     * 文档名称
     */
    private final String name;

    /**
     * 文档描述
     */
    private final String desc;

    /**
     * 环境地址
     */
    // private final   String domain;

    /**
     * 接口地址
     */
    private final String path;

    /**
     * 请求方式 GET POST PUT DELETE HEAD OPTIONS PATCH
     */
    private final String method;

    /**
     * headers
     */
    private final List<DocViewParamData> requestHeaderDataList;

    private final String requestHeader;

    /**
     * 请求参数
     */
    private final List<DocViewParamData> requestParamDataList;

    private final String requestParam;

    /**
     * 请求参数
     */
    private final List<DocViewParamData> requestBodyDataList;

    /**
     * 请求中 body 参数
     */
    private final String requestBody;

    /**
     * 请求示例
     */
    private final String requestExample;

    /**
     * 返回参数
     */
    private final List<DocViewParamData> responseParamDataList;
    private final String responseParam;



    /**
     * 返回示例
     */
    private final String responseExample;

    private final String type;

    public DocViewData(DocView docView) {

        Settings settings = Settings.getInstance(docView.getPsiClass().getProject());

        this.name = docView.getName();
        this.desc = docView.getDesc();
        this.path = docView.getPath();
        this.method = docView.getMethod();
        this.type = docView.getType().toString();

        this.requestHeaderDataList = headerDataList(docView.getHeaderList());
        this.requestHeader = headerMarkdown(requestHeaderDataList);

        this.requestParamDataList = paramDataList(docView.getReqParamList());
        this.requestParam = paramMarkdown(requestParamDataList, ParamTypeEnum.REQUEST_PARAM);


        //请求参数
        this.requestBodyDataList = buildBodyDataList(docView.getReqBody().getChildList());
        this.requestBody = settings.getSeparateParam() ? separateParamMarkdown(requestBodyDataList) : paramMarkdown(requestBodyDataList, ParamTypeEnum.REQUEST_BODY);
        this.requestExample = requestExample(docView);

        //返回参数
        this.responseParamDataList = buildBodyDataList(docView.getRespBody().getChildList());
        this.responseParam = settings.getSeparateParam() ? separateParamMarkdown(responseParamDataList) : paramMarkdown(responseParamDataList,ParamTypeEnum.RESPONSE_PARAM);
        this.responseExample = respBodyExample(docView.getRespExample());

    }

    @NotNull
    @Contract("_ -> new")
    public static DocViewData getInstance(@NotNull DocView docView) {
        return new DocViewData(docView);
    }


    @NotNull
    public static String toDDL(PsiClass psiClass, List<DocViewParamData> dataList) {
        String className = SpringPsiUtils.className(psiClass);

        String comment = SpringPsiUtils.classComment(psiClass);

        String ddl="-- auto Generated\n" +
                "-- DROP TABLE IF EXISTS "+className+";\n"+
                "CREATE TABLE "+className+"(\n"+
                toFiled(dataList)+
                getIdFiled(dataList)+
                ")"+
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '"+comment+"';";

        return ddl;
    }



    public static String getIdFiled(List<DocViewParamData> dataList){

        for (DocViewParamData docViewParamData : dataList) {
            boolean id = docViewParamData.isId();
            if(id){
                return "  PRIMARY KEY ("+docViewParamData.getName()+")\n";
            }
            }

        return "PRIMARY KEY (id)";
    }
    public static String toFiled(List<DocViewParamData> dataList){

        StringBuilder stringBuilder = new StringBuilder();

        for (DocViewParamData docViewParamData : dataList) {
            //不存在数据库的不处理
            boolean exist = docViewParamData.isExist();
            if(exist==false){
                continue;
            }
            String name = docViewParamData.getName();
            name=SpringPsiUtils.camel4underline(name);
            String type = docViewParamData.getType();
            Boolean required = docViewParamData.getRequired();
            String desc1 = docViewParamData.getDesc();
            boolean id = docViewParamData.isId();

            stringBuilder.append("  "+name+" ");

            boolean json = docViewParamData.isJson();

            if(json){
                type="json";
            }

            String typeSql = dealType(type);


            stringBuilder.append(typeSql);

            if(!id) {
                if (required) {
                    stringBuilder.append(" NOT NULL");
                }
            }else {
                stringBuilder.append("NOT NULL AUTO_INCREMENT");
            }

            stringBuilder.append(" COMMENT '"+desc1+"'");


            stringBuilder.append(",\n");
        }

        return  stringBuilder.toString();
    }

    private static String dealType(String type){

        if(type.equals("String")){
            return  " VARCHAR (50) DEFAULT '' ";
        }else if(type.equals("Integer")){
            return  " INT DEFAULT -1";
        }else if(type.equals("Long")){
            return  " BIGINT (15) ";
        }else if(type.equals("Double")){
            return  " DOUBLE DEFAULT 0.0 ";
        }else if(type.equals("Float")){
            return  " FLOAT DEFAULT 0.0 ";
        }else if(type.equals("Boolean")){
            return  " TINYINT DEFAULT -1 ";
        }else if(type.equals("Date")){
            return  " TIMESTAMP DEFAULT now()";
        }else if(type.equals("Time")){
            return  " TIMESTAMP DEFAULT now() ";
        }else if(type.equals("Timestamp")) {
            return " TIMESTAMP DEFAULT now() ";
        }else if(type.equals("json")){
            return " JSON ";
        }else{
            return  "VARCHAR (50) DEFAULT '' ";
        }
    }


    //生成markdown
    public static String markdownText(Project project, DocView docView) {

        DocViewData docViewData = new DocViewData(docView);

        if (docView.getType() == FrameworkEnum.DUBBO) {
            return VelocityUtils.convert(TemplateSettings.getInstance(project).getDubboTemplate(), docViewData);
        } else {
            // 按照 Spring 模版
            return VelocityUtils.convert(TemplateSettings.getInstance(project).getSpringTemplate(), docViewData);
        }
    }

    /**
     * dataList 转为 Markdown 文本
     *
     * @param dataList
     * @return
     */
    @NotNull
    public static String paramMarkdown(List<DocViewParamData> dataList,ParamTypeEnum paramType) {

        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        if(paramType.equals(ParamTypeEnum.REQUEST_PARAM) || paramType.equals(ParamTypeEnum.REQUEST_BODY)) {

            return "|参数名|类型|必选|可筛选|可更新|描述|版本|\n"
                    + "|:-----|:-----|:-----|:-----|:-----|:-----|:-----|\n"
                    + paramMarkdownContent(dataList, paramType);
        }else  {
            return "|参数名|类型|必选|描述|版本|\n"
                    + "|:-----|:-----|:-----|:-----|:-----|\n"
                    + paramMarkdownContent(dataList, paramType);
        }
    }

    /**
     * 切分多个展示
     */
    private static String separateParamMarkdown(List<DocViewParamData> dataList) {
        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }
        List<DocViewParamData> paramDataList = new ArrayList<>();

        StringBuilder builder = new StringBuilder();
        builder.append("|参数名|类型|必选|可筛选|可更新|描述|版本|\n")
                .append("|:-----|:-----|:-----|:-----|:-----|:-----|:-----|\n");
        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getType())
                    .append("|").append(data.getRequired() ? "是" : "否")
                    .append("|").append(data.getFilterable()? "是" : "否")
                    .append("|").append(data.getUpdateable() ? "是" : "否")
                    .append("|").append(data.getDesc())
                    .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                    .append("|").append("\n");
            if (CollectionUtils.isNotEmpty(data.getChildList())) {
                paramDataList.add(data);
            }
        }
        builder.append(separateSubParamMarkdown(paramDataList));
        return builder.toString();
    }

    /**
     * 构造子的参数 Markdown 实体
     */
    private static String separateSubParamMarkdown(List<DocViewParamData> dataList) {
        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (DocViewParamData data : dataList) {
            List<DocViewParamData> childList = data.getChildList();

            if (CollectionUtils.isNotEmpty(childList)) {
                if (childList.size() == 1) {
                    DocViewParamData docViewParamData = childList.get(0);
                    if (docViewParamData.isCollection()) {
                        builder.append("\n- ").append(docViewParamData.getType()).append(" ").append(docViewParamData.getName()).append("\n\n");
                        builder.append(separateParamMarkdown(docViewParamData.getChildList()));
                        continue;
                    }
                }
                if (childList.size() == 2) {
                    DocViewParamData docViewParamData = childList.get(1);
                    if (docViewParamData.isMap()) {
                        builder.append("\n- ").append(docViewParamData.getType()).append(" ").append(docViewParamData.getName()).append("\n\n");
                        builder.append(separateParamMarkdown(docViewParamData.getChildList()));
                        continue;
                    }
                }
            }


            builder.append("\n- ").append(data.getType()).append(" ").append(data.getName()).append("\n\n");
            builder.append(separateParamMarkdown(data.getChildList()));
        }

        return builder.toString();
    }


    /**
     * 表格内数据
     */
    public static StringBuilder paramMarkdownContent(List<DocViewParamData> dataList,ParamTypeEnum paramType) {

        StringBuilder builder = new StringBuilder();


        if(paramType.equals(ParamTypeEnum.REQUEST_PARAM) || paramType.equals(ParamTypeEnum.REQUEST_BODY)) {
            for (DocViewParamData data : dataList) {
                builder.append("|").append(data.getPrefixSymbol1()).append(data.getPrefixSymbol2()).append(data.getName())
                        .append("|").append(data.getType())
                        .append("|").append(data.getRequired() ? "是" : "否")
                        .append("|").append(data.getFilterable() ? "是" : "否")
                        .append("|").append(data.getUpdateable() ? "是" : "否")
                        .append("|").append(data.getDesc())
                        .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                        .append("|").append("\n");
                if (CollectionUtils.isNotEmpty(data.getChildList())) {
                    builder.append(paramMarkdownContent(data.getChildList(),paramType));
                }
            }
        }else{

            for (DocViewParamData data : dataList) {
                builder.append("|").append(data.getPrefixSymbol1()).append(data.getPrefixSymbol2()).append(data.getName())
                        .append("|").append(data.getType())
                        .append("|").append(data.getRequired() ? "是" : "否")
                        .append("|").append(data.getDesc())
                        .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                        .append("|").append("\n");
                if (CollectionUtils.isNotEmpty(data.getChildList())) {
                    builder.append(paramMarkdownContent(data.getChildList(),paramType));
                }
            }

        }

        return builder;
    }

    //头
    @NotNull
    public static String headerMarkdown(List<DocViewParamData> dataList) {

        if (CollectionUtils.isEmpty(dataList)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (DocViewParamData data : dataList) {
            builder.append("|").append(data.getName())
                    .append("|").append(data.getExample())
                    .append("|").append(data.getRequired() ? "是" : "否")
                    .append("|").append(data.getDesc())
                    .append("|").append(Arrays.stream(new String[]{data.getSince(), data.getVersion()}).filter(StringUtils::isNotBlank).collect(Collectors.joining("-")))
                    .append("|").append("\n");
        }

        return "|参数名|参数值|必填|描述|版本|\n"
                + "|:-----|:-----|:-----|:-----|:-----|\n"
                + builder;
    }

    private List<DocViewParamData> headerDataList(List<Header> headerList) {

        if (CollectionUtils.isEmpty(headerList)) {
            return new ArrayList<>();
        }

        return headerList.stream().map(header -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(header.getPsiElement());
            data.setName(header.getName());
            data.setExample(header.getValue());
            data.setRequired(header.getRequired());
            data.setDesc(StringUtils.isNotBlank(header.getDesc()) ? header.getDesc() : "");

            return data;
        }).collect(Collectors.toList());
    }

    private List<DocViewParamData> paramDataList(List<Param> reqParamList) {
        if (CollectionUtils.isEmpty(reqParamList)) {
            return new ArrayList<>();
        }

        return reqParamList.stream().map(param -> {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(param.getPsiElement());
            data.setExample(param.getExample());
            data.setName(param.getName());
            data.setRequired(param.getRequired());
            data.setUpdateable(param.getUpdateable());
            data.setFilterable(param.getFilterable());
            data.setType(param.getType());

            data.setJson(param.isJson());
            data.setExist(param.isExist());

            data.setDesc(StringUtils.isNotBlank(param.getDesc()) ? param.getDesc() : "");

            data.setVersion(param.getVersion());
            data.setSince(param.getSince());
            if (StringUtils.isNotBlank(data.getDesc())) {
                if (data.getDesc().contains("@since") || data.getDesc().contains("@version")) {
                    data.setSince("");
                    data.setDesc("");
                }
            }

            return data;
        }).collect(Collectors.toList());
    }

    /**
     * 根据 bodyList 构建参数集合
     *
     * @param bodyList
     * @return
     */
    @NotNull
    public static List<DocViewParamData> buildBodyDataList(List<Body> bodyList) {

        if (CollectionUtils.isEmpty(bodyList)) {
            return new ArrayList<>();
        }

        return buildBodyDataList(bodyList, "", "");
    }

    /**
     * 请求参数或者返回参数都在这
     *
     * @param bodyList
     * @param prefixSymbol1,
     * @param prefixSymbol2
     */
    @NotNull
    private static List<DocViewParamData> buildBodyDataList(@NotNull List<Body> bodyList, String prefixSymbol1, String prefixSymbol2) {

        List<DocViewParamData> dataList = new ArrayList<>();

        for (Body body : bodyList) {

            DocViewParamData data = new DocViewParamData();
            data.setPsiElement(body.getPsiElement());
            data.setName(body.getName());
            data.setExample(body.getExample());
            data.setRequired(body.getRequired());
            data.setType(body.getType());

            //sql相关的两个字段
            data.setJson(body.isJson());
            data.setExist(body.isExist());
            data.setId(body.isId());

            data.setDesc(StringUtils.isNotBlank(body.getDesc()) ? body.getDesc() : "");

            data.setVersion(body.getVersion());
            data.setSince(body.getSince());
            if (StringUtils.isNotBlank(data.getDesc())) {
                if (data.getDesc().contains("@since") || data.getDesc().contains("@version")) {
                    data.setSince("");
                    data.setDesc("");
                }
            }

            data.setPrefixSymbol1(prefixSymbol1);
            data.setPrefixSymbol2(prefixSymbol2);
            data.setCollection(body.isCollection());
            data.setMap(body.isMap());

            if (CollectionUtils.isNotEmpty(body.getChildList())) {

                Settings settings = Settings.getInstance(body.getPsiElement().getProject());

                data.setChildList(
                        buildBodyDataList(body.getChildList(), settings.getPrefixSymbol1(), prefixSymbol2 + settings.getPrefixSymbol2()));
            }
            dataList.add(data);
        }
        return dataList;
    }

    @NotNull
    private String requestExample(DocView docView) {

        String reqFormExample = reqFormExample(docView.getReqFormExample());

        String reqBodyExample = reqBodyExample(docView.getReqBodyExample());

        if (StringUtils.isBlank(reqFormExample)) {
            return reqBodyExample;
        }

        if (StringUtils.isBlank(reqBodyExample)) {
            return reqFormExample;
        }

        return reqFormExample + "\n\n" + reqBodyExample;
    }

    /**
     * 请求参数中的 Form 示例
     *
     * @param reqFormExample
     * @return
     */
    private String reqFormExample(String reqFormExample) {
        if (StringUtils.isBlank(reqFormExample)) {
            return "";
        }

        return "```Form\n" +
                reqFormExample + "\n" +
                "```";
    }

    /**
     * 请求参数中的 Body 示例
     *
     * @param reqBodyExample Body
     * @return 组装结果
     */
    @NotNull
    @Contract(pure = true)
    private String reqBodyExample(String reqBodyExample) {

        if (StringUtils.isBlank(reqBodyExample)) {
            return "";
        }

        return "```JSON\n"
                + reqBodyExample + "\n"
                + "```";
    }

    /**
     * 构建返回 body
     *
     * @param respExample 返回示例
     * @return 返回body
     */
    @NotNull
    @Contract(pure = true)
    private String respBodyExample(String respExample) {

        if (StringUtils.isBlank(respExample)) {
            return "";
        }

        return "```JSON\n"
                + respExample + "\n"
                + "```\n\n";
    }

}
