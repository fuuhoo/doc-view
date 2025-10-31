package com.liuzhihang.doc.view.dto;

import com.intellij.psi.PsiElement;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数 data 数据
 *
 * @author liuzhihang
 * @date 2020/2/27 16:39
 */
@Data
public class DocViewParamData {

    /**
     * 参数的 psiElement
     */
    private PsiElement psiElement;

    /**
     * 前缀 1
     */
    private String prefixSymbol1 = "";

    /**
     * 前缀 2
     */
    private String prefixSymbol2 = "";

    /**
     * 参数名
     */
    private String name;

    /**
     * 类型
     */
    private String type;

    /**
     * 是否必须
     */
    private Boolean required;


    /**
     * 是否可以作为筛选条件
     */

    private Boolean filterable=false;


    /**
     * 是否可更新
     */

    private Boolean updateable=false;


    /**
     * 参数示例
     */
    private String example;

    /**
     * 参数描述
     */
    private String desc = "";

    /**
     * since
     */
    private String since;

    /**
     * version
     */
    private String version="无";

    /**
     * 子
     */
    private List<DocViewParamData> childList = new ArrayList<>();

    /**
     * 是否是集合
     */
    private boolean isCollection = false;

    /**
     * 是否是 map
     */
    private boolean isMap = false;


    /**
     * 是否是 json
     */
    private boolean isJson ;


    /**
     * 是否在数据库存在
     */
    private boolean exist ;


    /**
     * 是否是 json
     */
    private boolean isId ;



    /**
     * 是否忽略输入
     */
    private boolean ifIgnoreRead=false ;


    /**
     * 是否忽略输出
     */
    private boolean ifIgnoreWrite=false ;

}
