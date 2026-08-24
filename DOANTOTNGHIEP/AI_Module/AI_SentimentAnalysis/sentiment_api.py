import pickle

from flask import Flask, jsonify, request
import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences

from sentiment_preprocessing import clean_text, load_stopwords


app = Flask(__name__)

MODEL_PATH = "phan_tich_cam_xuc.keras"
TOKENIZER_PATH = "tokenizer.pkl"
MAX_LENGTH = 300
THRESHOLD = 0.5

model = tf.keras.models.load_model(MODEL_PATH, compile=False)
with open(TOKENIZER_PATH, "rb") as f:
    tokenizer = pickle.load(f)


stopwords = load_stopwords()


def to_sequence(text):
    return tokenizer.texts_to_sequences([text])[0]


def predict_with_model(text):
    cleaned_text, preprocessing = clean_text(text, stopwords)
    sequence = to_sequence(cleaned_text)
    if not sequence and preprocessing == "stopwords_removed":
        cleaned_text, preprocessing = clean_text(text, set())
        sequence = to_sequence(cleaned_text)

    padded = pad_sequences(
        [sequence],
        maxlen=MAX_LENGTH,
        padding="post",
        truncating="post",
    )
    prediction = model.predict(padded, verbose=0)
    score = float(prediction[0][0])
    sentiment = "positive" if score > THRESHOLD else "negative"
    return sentiment, score, cleaned_text, sequence, preprocessing


@app.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json(silent=True) or {}
        text = data.get("text", "").strip()
        debug = bool(data.get("debug", False))

        if not text:
            return jsonify({"error": "No text provided"}), 400

        sentiment, confidence, cleaned_text, sequence, preprocessing = predict_with_model(text)
        result = {
            "sentiment": sentiment,
            "confidence": confidence,
            "message": (
                f"Phan hoi {'tich cuc' if sentiment == 'positive' else 'tieu cuc'} "
                f"(do tin cay: {confidence:.4f})"
            ),
            "source": "model",
        }

        if debug:
            result["debug"] = {
                "cleaned_text": cleaned_text,
                "preprocessing": preprocessing,
                "token_count": len(sequence),
                "sequence": sequence,
            }

        return jsonify(result)

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "API is running"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
