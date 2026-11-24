from pathlib import Path
import os,subprocess,hashlib
root=Path(__file__).resolve().parent
os.chdir(root)
suites='DL52Test,DL51Test,DL50Test,DL49Test,DL37Test,DL34Test,DL34PivotOrderTest,DL33Test,DL32Test,DL31Test,DL30Test,DL29CoversTest,DL29UiTest,DL28DeleteProviderTest,DL28PolishTest,DL27BackTest,DL27CopiesTest,DL26VisualTest,DL26PickerTest,DL26DefaultTabTest,DL25PlaylistTest,GenericRadioTest,RadioRowTest,NativeIdentityTest,DL24AdapterOrderTest,DL24PinPerformanceTest,PinCurrentWireTest,LibraryPinOrderWireTest,ArtTest,NativeMainListIntegrationTest,NativeSelectionUiTest,DL24PerformanceTest,DL23RegressionTest,LocalAudioTest,SelectionModeTest,LocalAudioNameMatchTest,PlaylistMirrorTest,NativeIdentityTest'
maven=os.environ.get('MUSIC_MAVEN','mvn')
with open('QA.log','w') as log:
 result=subprocess.run([maven,'-B','-Dtest='+suites,'test'],stdout=log,stderr=subprocess.STDOUT)
print(Path('QA.log').read_text())
if result.returncode:raise SystemExit(result.returncode)
print('QA_COMPLETED')
