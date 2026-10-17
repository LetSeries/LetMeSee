package com.letmesee;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 只读虚拟库存的标记。
 *
 * <p>{@link InventoryListener} 通过它识别只读界面并取消一切点击；
 * 同时按 {@link InventoryHolder} 契约返回创建出来的库存，而不是 null，
 * 避免其它插件在事件里回调 {@code getInventory()} 时踩空。</p>
 */
public class ReadOnlyHolder implements InventoryHolder {

    private volatile Inventory inventory;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 由创建方在库存构造完成后回填自身引用。
     * 构造期不能自行创建库存（会形成鸡生蛋），因此单独暴露。
     */
    public ReadOnlyHolder attach(Inventory inventory) {
        this.inventory = inventory;
        return this;
    }
}
