package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 宿主注入树的外部表示；Kit 不解释配置格式或节点业务语义。 */
public interface UiTreeCodec<N, S> {
    DecodeResult<N> decode(@Nullable S source);

    /** 无根节点的外部表示由宿主决定，可返回 null 表示省略。 */
    @Nullable S encode(@Nullable N root);

    /** 成功且 root 为 null 表示空树；error 非空表示无法解码，不能当空树覆盖原内容。 */
    record DecodeResult<N>(@Nullable N root, @Nullable Component error) {
        public boolean succeeded() { return error == null; }
    }
}
