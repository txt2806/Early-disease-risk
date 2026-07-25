import 'package:flutter/material.dart';

class SymptomChip extends StatelessWidget {
  final String label;
  final bool isSelected;
  final ValueChanged<bool> onSelected;
  final bool isUrgent;

  const SymptomChip({
    super.key,
    required this.label,
    required this.isSelected,
    required this.onSelected,
    this.isUrgent = false,
  });

  @override
  Widget build(BuildContext context) {
    final activeColor = isUrgent ? const Color(0xFFEF4444) : Colors.teal;

    return FilterChip(
      label: Text(label),
      selected: isSelected,
      onSelected: onSelected,
      selectedColor: activeColor.withValues(alpha: 0.2),
      checkmarkColor: activeColor,
      labelStyle: TextStyle(
        color: isSelected ? activeColor : Colors.black87,
        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        fontSize: 13,
      ),
      backgroundColor: Colors.grey.shade100,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(
          color: isSelected ? activeColor : Colors.grey.shade300,
          width: isSelected ? 1.5 : 1.0,
        ),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
    );
  }
}
