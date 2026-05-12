package com.huanghuang.SRfix.util; // 注意：包名改成了 .util

public interface IAltarModeHolder {
    boolean yuusha$isAutoMode();
    void yuusha$setAutoMode(boolean autoMode);
    void yuusha$sync(); // 新增同步方法定义
}