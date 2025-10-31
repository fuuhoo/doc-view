package com.liuzhihang.doc.view.dto;

import com.intellij.psi.PsiElement;
import lombok.Data;

/**
 * @author liuzhihang
 * @date 2020/2/27 16:39
 */
@Data
public class Param {

    /**
     * 参数的 psiElement
     */
    private PsiElement psiElement;

    /**
     * 是否必须
     */
    private Boolean required;
    /**
     * 参数名
     */
    private String name;
    /**
     * 参数示例
     */
    private String example;

    /**
     * 参数描述
     */
    private String desc;


    /**
     * 是否可作为筛选条件
     */
    private Boolean filterable;


    /**
     * 是否可更新
     */

    private Boolean updateable;


    /**
     * 类型
     */
    private String type;


    /**
     * since
     */
    private String since;

    /**
     * version
     */
    private String version="无";


    /**
     * 是否是 json
     */
    private boolean isJson ;


    /**
     * 是否在数据库存在
     */
    private boolean exist ;


    /**
     * 是否忽略输入
     */
    private boolean ifIgnoreRead=false ;


    /**
     * 是否忽略输出
     */
    private boolean ifIgnoreWrite=false ;


}
