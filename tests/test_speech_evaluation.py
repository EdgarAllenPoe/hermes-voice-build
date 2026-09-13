import importlib.util,json,struct,tempfile,unittest,wave
from pathlib import Path
R=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('speech_eval',R/'tools/evaluate_speech.py')
E=importlib.util.module_from_spec(spec);spec.loader.exec_module(E)
class SpeechEvaluationTests(unittest.TestCase):
    def test_word_error_scoring(self):
        self.assertEqual(E.word_errors('One, TWO three.','one two three')['wer'],0)
        self.assertEqual(E.word_errors('one two three','one four three')['errors'],1)
        self.assertEqual(E.word_errors('one two three','one three')['errors'],1)
    def test_silence_hallucination_report(self):
        r=E.word_errors('','invented words');self.assertIsNone(r['wer']);self.assertEqual(r['unexpected_words'],2)
    def test_preparation_has_no_agent_or_transcriber(self):
        with tempfile.TemporaryDirectory() as t:
            p=Path(t);wav=p/'speech.wav'
            with wave.open(str(wav),'wb') as w:
                w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(struct.pack('<320h',*([100,-100]*160)))
            manifest=p/'cases.json';manifest.write_text(json.dumps([dict(name='test',wav='speech.wav',reference='words')]))
            result=E.evaluate(manifest,p/'run')
            self.assertFalse(result['hermes_invoked']);self.assertFalse(result['transcription_run'])
            self.assertTrue((p/'run/1/compressed.wav').is_file())
            with self.assertRaises(ValueError):E.evaluate(manifest,p/'run')
