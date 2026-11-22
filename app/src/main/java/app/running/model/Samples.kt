package app.running.model

import app.running.model.BlockType.*
import app.running.model.EndCondition.*

private fun time(min: Int, sec: Int = 0) = Time(min * 60 + sec)
private fun pace(fast: Int, slow: Int) = BlockTarget(TargetKind.PACE, fast.toDouble(), slow.toDouble())
private fun blk(type: BlockType, end: EndCondition, target: BlockTarget = BlockTarget()) = Block(type = type, end = end, target = target)

fun sampleWorkouts(): List<Workout> = listOf(
    Workout(
        name = "Series 6×400 m",
        items = listOf(
            SingleItem(block = blk(WARMUP, time(10))),
            GroupItem(
                repeats = 6,
                blocks = listOf(
                    blk(WORK, Distance(400), pace(230, 250)),
                    blk(RECOVERY, time(1, 30)),
                ),
            ),
            SingleItem(block = blk(COOLDOWN, time(5))),
        ),
    ),
    Workout(
        name = "Rodaje suave 40'",
        items = listOf(SingleItem(block = blk(WORK, time(40), pace(340, 370)))),
    ),
    Workout(
        name = "Fartlek 8×(1' + 1')",
        items = listOf(
            SingleItem(block = blk(WARMUP, time(10))),
            GroupItem(
                repeats = 8,
                blocks = listOf(
                    blk(WORK, time(1), BlockTarget(TargetKind.SPEED, 14.0, 16.0)),
                    blk(RECOVERY, time(1)),
                ),
            ),
            SingleItem(block = blk(COOLDOWN, time(8))),
        ),
    ),
    Workout(
        name = "Progresivo 5 km",
        items = listOf(
            SingleItem(block = blk(WORK, Distance(2000), pace(360, 380))),
            SingleItem(block = blk(WORK, Distance(2000), pace(330, 350))),
            SingleItem(block = blk(WORK, Distance(1000), pace(300, 320))),
        ),
    ),
)
