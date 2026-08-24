from pathlib import Path
from urllib.request import urlopen
import re
import string

from pyvi import ViTokenizer


STOPWORDS_URL = (
    "https://raw.githubusercontent.com/stopwords/vietnamese-stopwords/"
    "master/vietnamese-stopwords.txt"
)
LOCAL_STOPWORDS_PATHS = (
    Path("ntc-scv/vietnamese-stopwords.txt"),
    Path("ntc-scv/data/vietnamese-stopwords.txt"),
    Path("vietnamese-stopwords.txt"),
)


def load_stopwords():
    for path in LOCAL_STOPWORDS_PATHS:
        if path.exists():
            return {
                line.strip()
                for line in path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            }

    try:
        with urlopen(STOPWORDS_URL, timeout=10) as response:
            return {
                line.decode("utf-8").strip()
                for line in response
                if line.strip()
            }
    except Exception as exc:
        local_paths = ", ".join(str(path) for path in LOCAL_STOPWORDS_PATHS)
        raise RuntimeError(
            "Cannot load Vietnamese stopwords. Add a local stopword file at one "
            f"of: {local_paths}"
        ) from exc


def normalize_text(text):
    text = re.sub(r"https?://\S+|www\.\S+", "", text)
    text = re.sub(r"\b\d+\b", "", text)
    text = re.sub(r"<.*?>+", "", text)
    text = re.sub("[%s]" % re.escape(string.punctuation), " ", text)
    text = re.sub(r"[\u2018\u2019\u201c\u201d\u2026]", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip().lower()


def remove_stopwords(text, stopwords):
    words = text.split()
    filtered_words = [word for word in words if word not in stopwords]
    return " ".join(filtered_words)


def clean_text(text, stopwords):
    normalized_text = normalize_text(text)
    stopword_text = remove_stopwords(normalized_text, stopwords)

    # Keep using stopwords, but do not turn short inputs such as "hay"
    # into an empty sequence. Empty text trains/inferes as all-padding.
    if stopword_text:
        return ViTokenizer.tokenize(stopword_text), "stopwords_removed"

    return ViTokenizer.tokenize(normalized_text), "raw_tokenized_fallback"
