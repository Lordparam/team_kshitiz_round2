package com.example.campusnavpro
  data class Node(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val floor: Int = 0,
    val type: NodeType = NodeType.PATH
)

data class Edge(
    val fromNodeId: String,
    val toNodeId: String,
    val distance: Double,
    val isAccessible: Boolean = true
)

enum class NodeType {
    ROOM, ENTRANCE, PATH, STAIRS, ELEVATOR
}