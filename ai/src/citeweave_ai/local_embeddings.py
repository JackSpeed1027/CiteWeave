"""本机 BGE 中文向量适配器，供离线检索实验复用。"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from numpy.typing import NDArray

DIMENSIONS = 512
MAX_TOKENS = 512
QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章："


class LocalBge:
    """固定 CPU、CLS 池化及 L2 归一化。权重和词表从明确目录本地读取。"""

    def __init__(self, model_dir: Path, threads: int = 4) -> None:
        import onnxruntime as ort
        from tokenizers import Tokenizer

        self._tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        pad_id = self._tokenizer.token_to_id("[PAD]")
        if pad_id is None:
            raise ValueError("模型缺少 [PAD] 标识")
        self._tokenizer.enable_padding(pad_id=pad_id)
        self._tokenizer.enable_truncation(max_length=MAX_TOKENS)
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads
        options.inter_op_num_threads = 1
        self._session = ort.InferenceSession(str(model_dir / "model_optimized.onnx"), options,
                                             providers=["CPUExecutionProvider"])
        self._inputs = {item.name for item in self._session.get_inputs()}

    def encode(self, texts: list[str], *, query: bool = False) -> NDArray:
        """问题加检索指令，正文保持原文，返回 512 维单位向量。"""
        import numpy as np

        if not texts or any(not text.strip() for text in texts):
            raise ValueError("向量输入应至少有一条非空文本")
        batch = self._tokenizer.encode_batch([QUERY_PREFIX + text if query else text for text in texts])
        available = {
            "input_ids": [item.ids for item in batch],
            "attention_mask": [item.attention_mask for item in batch],
            "token_type_ids": [item.type_ids for item in batch],
        }
        inputs = {key: np.asarray(available[key], dtype=np.int64) for key in self._inputs}
        hidden = self._session.run(["last_hidden_state"], inputs)[0]
        vectors = hidden[:, 0, :]
        norms = np.linalg.norm(vectors, axis=1, keepdims=True)
        if vectors.shape != (len(texts), DIMENSIONS) or not np.isfinite(vectors).all() or (norms == 0).any():
            raise ValueError("模型输出维度或数值不符合 BGE 契约")
        return vectors / norms
