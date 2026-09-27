package com.huanghuang.SRfix.util; // 注意：包名改成了 .util

public interface IAltarModeHolder {
    boolean huanghuang$isAutoMode();
    void huanghuang$setAutoMode(boolean autoMode);
    void huanghuang$sync(); // 新增同步方法定义
}