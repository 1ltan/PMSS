"""Generate the compact four-state sensor classifier bundled with the app."""

from pathlib import Path

import numpy as np
import tensorflow as tf


OUTPUT_PATH = Path(__file__).parents[1] / "app/src/main/assets/activity_classifier.tflite"


def build_model() -> tf.keras.Model:
    model = tf.keras.Sequential(
        [
            tf.keras.Input(shape=(40,), name="sensor_features"),
            tf.keras.layers.Dense(4, activation="softmax", name="blind_state_probabilities"),
        ]
    )
    weights = np.zeros((40, 4), dtype=np.float32)
    weights[:20] = np.array([-8, 4, 10, 2], dtype=np.float32) / 20
    weights[20:] = np.array([-8, 2, 8, 25], dtype=np.float32) / 20
    biases = np.array([3, 1, -1, -4], dtype=np.float32)
    model.layers[0].set_weights([weights, biases])
    return model


def main() -> None:
    converter = tf.lite.TFLiteConverter.from_keras_model(build_model())
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_bytes(converter.convert())
    print(f"Generated {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
