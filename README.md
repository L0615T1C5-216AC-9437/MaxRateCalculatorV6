[![Github All Releases](https://img.shields.io/github/downloads/L0615T1C5-216AC-9437/MaxRateCalculatorV6/total.svg)]()
![GitHub issues](https://img.shields.io/github/issues/L0615T1C5-216AC-9437/MaxRateCalculatorV6)
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/downloads-pre/L0615T1C5-216AC-9437/MaxRateCalculatorV6/1.5.1/total) 
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/downloads-pre/L0615T1C5-216AC-9437/MaxRateCalculatorV6/3.0-pa-0.1/total)  
*A V7 Version of this mod can be found [here](https://github.com/L0615T1C5-216AC-9437/MaxRateCalculator).*

# Old Mod below, several changes made in the last release

# Max Rate Calculator
A mindustry mod that allows you to calculate the efficiency, bottle neck and ratios of your machinations.  
*may or may not work with modded items / blocks*

<img src="docs/CalculateReal.png" alt="RealRatio" width="400"/><img src="docs/CalculateMax.png" alt="MaxRatio" width="400"/>

## Installation Guide
1. Download the latest mod verion in [#Releases](https://github.com/L0615T1C5-216AC-9437/MaxRateCalculatorV6/releases).
2. Open mindustry, go to `mods` section, select `Open Folder`
3. Move the mod (`Jar` file) into the folder
4. Reopen mindustry.
   If you get a welcome message, the mod was successfully installed

## Usage
The Max Rate is ran by selecting the are you want the mrc[1] to calculate. This is done by pressing the **\[ ` ~ ]** button in your keyboard and selecting a area as if you were making a schematic.
1. Press **\[ ` ~ ]** in your keyboard
2. Move your mouse
3. Select what type of calculation you want (Max Rate, Real Rate, etc.)
4. Profit.
## Minutiae
- Calculations with weapons will assume they are always shooting

**Calculate Real**  
- If a weapon does not have coolant, it will not boost or consume coolant, even if available.
- If a drill does not have coolant, it will not boost or consume coolant, even if available.

**Calculate Max**  
- If a weapon does not have coolant, it will use the best available coolant inside the selection area.  
- If no coolant is available inside the selection area, it will default to cryo-fluid.

## Building Your own .Jar

1. Install JDK 16. If you don't know how, look it up.
2. Open CMD/Terminal to the plugin directory. The one were the gradle.bat file is located
3. Type and Run `gradlew jar` [2].
4. Your mod jar will be in the `build/libs` directory.

## Android?
**no.**

## Supported Languages
English (en)  
Spanish (es)  
*This mod will automatically switch language if a Language Pack is available.*

## Need Help?
If you have any questions on how to run / build / use this mod you can contact me through discord.  
Discord: `L0615T1C4L.N16HTM4R3#6238` ( I respond to DM's)  
Discord-Server: https://discord.gg/e5t2672rm2

--- 
*[1]* *Acronym for Max Rate Calculator*  
*[2]* *On Linux/Mac it's `./gradlew`, If the terminal returns Permission denied or Command not found run `chmod +x ./gradlew`*
