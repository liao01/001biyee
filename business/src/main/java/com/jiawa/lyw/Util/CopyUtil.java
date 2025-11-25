package com.jiawa.lyw.Util;

import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

public class CopyUtil {

        /**
         * 复制单个对象
         *
         * @param source 源对象
         * @param clazz  目标类
         * @param <T>    目标类型
         * @return 复制后的目标对象
         */
        public static <T> T copy(Object source, Class<T> clazz) {
            if (source == null) {
                return null;
            }
            T obj = null;
            try {
                obj = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            BeanUtils.copyProperties(source, obj);
            return obj;
        }

        /**
         * 复制集合
         *
         * @param sourceList 源列表
         * @param clazz      目标类
         * @param <T>        目标类型
         * @return 目标类型列表
         */
        public static <T> List<T> copyList(List<?> sourceList, Class<T> clazz) {
            List<T> targetList = new ArrayList<>();
            if (sourceList == null || sourceList.isEmpty()) {
                return targetList;
            }
            for (Object source : sourceList) {
                T target = copy(source, clazz);
                if (target != null) {
                    targetList.add(target);
                }
            }
            return targetList;
        }
}
